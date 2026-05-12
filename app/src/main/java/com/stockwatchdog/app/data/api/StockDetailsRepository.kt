package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.FinnhubEarningsCalendarEvent
import com.stockwatchdog.app.data.api.models.AlphaCompanyOverview
import com.stockwatchdog.app.data.api.models.YahooQuoteSummaryResult
import com.stockwatchdog.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fetches "extra" per-symbol details that don't fit in [Quote]:
 *  - next earnings date (epoch seconds)
 *  - last quarterly EPS actual vs estimate (beat / miss)
 *  - analyst average price target & recommendation
 *  - 52-week high / low
 *  - 200-day moving average
 *  - average daily volume + recent volume (volume spike detection)
 *
 * Source: Yahoo Finance `quoteSummary` (no API key required). Results are
 * cached in-memory with a TTL because fundamentals change at most once per
 * day. The cache lives for the process lifetime; that's enough since the
 * Dip screen re-fetches every time the user pulls to refresh.
 */
class StockDetailsRepository(
    private val yahoo: YahooFinanceApi,
    private val finnhub: FinnhubApi,
    private val alphaVantage: AlphaVantageApi,
    private val settings: SettingsRepository,
    private val cooldown: ProviderCooldown
) {

    /** Result TTL: 6 hours. Earnings/analyst data is daily-ish. */
    private val ttlMs: Long = 6 * 60 * 60 * 1000L

    private val cache: MutableMap<String, CachedDetails> = HashMap()
    private val lock = Mutex()

    private data class CachedDetails(
        val details: StockDetails,
        val fetchedAtMs: Long
    )

    /**
     * Get details for [symbol]. Returns `null` if the upstream is
     * unavailable (rate-limited, unauthorized, network error). Callers
     * should render UI gracefully without these fields.
     */
    suspend fun get(symbol: String, forceRefresh: Boolean = false): StockDetails? {
        val s = symbol.trim().uppercase()
        if (s.isBlank()) return null

        if (!forceRefresh) {
            lock.withLock {
                val hit = cache[s]
                if (hit != null && System.currentTimeMillis() - hit.fetchedAtMs < ttlMs) {
                    return hit.details
                }
            }
        }

        val yahooDetails = if (cooldown.isCoolingDown(YAHOO_SUMMARY_KEY)) {
            cachedOrNull(s)
        } else {
            fetchYahooDetails(s) ?: cachedOrNull(s)
        }

        val withEarnings = if (
            yahooDetails == null ||
            yahooDetails.nextEarningsEpochSeconds == null ||
            yahooDetails.nextEarningsQuarterLabel == null
        ) {
            yahooDetails.withEarningsFallback(fetchFinnhubEarnings(s), s)
        } else {
            yahooDetails
        }

        val fresh = withEarnings
            .withFundamentalsFallback(fetchAlphaOverviewIfNeeded(s, withEarnings), s)
            ?: return cachedOrNull(s)

        lock.withLock {
            cache[s] = CachedDetails(fresh, System.currentTimeMillis())
        }
        return fresh
    }

    /**
     * Bulk-fetch [symbols] sequentially so we stay polite to Yahoo's
     * undocumented endpoint. Cached symbols are served instantly.
     */
    suspend fun getMany(
        symbols: List<String>,
        forceRefresh: Boolean = false
    ): Map<String, StockDetails> {
        if (symbols.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, StockDetails>()
        for (s in symbols.distinct()) {
            val d = get(s, forceRefresh) ?: continue
            out[s] = d
        }
        return out
    }

    private suspend fun cachedOrNull(s: String): StockDetails? = lock.withLock {
        cache[s]?.details
    }

    private companion object {
        const val YAHOO_SUMMARY_KEY = "YAHOO_QUOTE_SUMMARY"
        const val FINNHUB_EARNINGS_KEY = "FINNHUB_EARNINGS"
        const val ALPHA_OVERVIEW_KEY = "ALPHA_OVERVIEW_DETAILS"
        val NAIROBI_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
        val FINNHUB_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    private suspend fun fetchYahooDetails(symbol: String): StockDetails? = withContext(Dispatchers.IO) {
        runCatching {
            val env = yahoo.quoteSummary(symbol)
            env.quoteSummary?.error?.description?.let { error(it) }
            val result = env.quoteSummary?.result?.firstOrNull() ?: return@runCatching null
            result.toDetails(symbol)
        }.onFailure {
            // Treat unauthorized/403 like a long cooldown; transient errors get a short cooldown.
            val msg = it.message ?: ""
            if ("401" in msg || "403" in msg || "Unauthorized" in msg) {
                cooldown.trip(YAHOO_SUMMARY_KEY, ProviderCooldown.PER_DAY_CAP_MS)
            } else {
                cooldown.trip(YAHOO_SUMMARY_KEY, ProviderCooldown.PER_MINUTE_CAP_MS)
            }
        }.getOrNull()
    }

    private suspend fun fetchFinnhubEarnings(symbol: String): StockDetails? {
        if (cooldown.isCoolingDown(FINNHUB_EARNINGS_KEY)) return null
        val apiKey = settings.settings.first().finnhubKey
        if (apiKey.isBlank()) return null

        val today = LocalDate.now(NAIROBI_ZONE)
        val from = today.minusDays(3)
        val to = today.plusMonths(15)

        return withContext(Dispatchers.IO) {
            runCatching {
                val response = finnhub.earningsCalendar(
                    from = from.format(FINNHUB_DATE),
                    to = to.format(FINNHUB_DATE),
                    symbol = symbol,
                    apiKey = apiKey
                )
                val event = response.earningsCalendar
                    .mapNotNull { event ->
                        val rawDate = event.date ?: return@mapNotNull null
                        val date = runCatching {
                            LocalDate.parse(rawDate, FINNHUB_DATE)
                        }.getOrNull() ?: return@mapNotNull null
                        event to date
                    }
                    .let { events ->
                        events
                            .filter { !it.second.isBefore(today) }
                            .minByOrNull { it.second }
                            ?: events
                                .filter { it.second.isBefore(today) && !it.second.isBefore(today.minusDays(3)) }
                                .maxByOrNull { it.second }
                    } ?: return@runCatching null

                event.first.toDetails(symbol, event.second)
            }.onFailure {
                val msg = it.message ?: ""
                val duration = if ("429" in msg || "limit" in msg.lowercase()) {
                    ProviderCooldown.PER_MINUTE_CAP_MS
                } else {
                    ProviderCooldown.PER_MINUTE_CAP_MS
                }
                cooldown.trip(FINNHUB_EARNINGS_KEY, duration)
            }.getOrNull()
        }
    }

    private suspend fun fetchAlphaOverviewIfNeeded(
        symbol: String,
        current: StockDetails?
    ): StockDetails? {
        if (current != null && !current.needsFundamentalFallback()) return null
        if (cooldown.isCoolingDown(ALPHA_OVERVIEW_KEY)) return null
        val apiKey = settings.settings.first().alphaVantageKey
        if (apiKey.isBlank()) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val response = alphaVantage.overview(symbol, apiKey)
                if (!response.note.isNullOrBlank() || !response.information.isNullOrBlank()) {
                    cooldown.trip(ALPHA_OVERVIEW_KEY, ProviderCooldown.PER_DAY_CAP_MS)
                    return@runCatching null
                }
                response.toDetails(symbol)
            }.onFailure {
                val msg = it.message ?: ""
                val duration = if ("limit" in msg.lowercase() || "quota" in msg.lowercase()) {
                    ProviderCooldown.PER_DAY_CAP_MS
                } else {
                    ProviderCooldown.PER_MINUTE_CAP_MS
                }
                cooldown.trip(ALPHA_OVERVIEW_KEY, duration)
            }.getOrNull()
        }
    }

    private fun StockDetails?.withEarningsFallback(
        fallback: StockDetails?,
        symbol: String
    ): StockDetails? {
        fallback ?: return this
        return this?.copy(
            nextEarningsEpochSeconds = nextEarningsEpochSeconds ?: fallback.nextEarningsEpochSeconds,
            nextEarningsIsEstimate = nextEarningsIsEstimate ?: fallback.nextEarningsIsEstimate,
            nextEarningsQuarterLabel = nextEarningsQuarterLabel ?: fallback.nextEarningsQuarterLabel,
            lastEpsActual = lastEpsActual ?: fallback.lastEpsActual,
            lastEpsEstimate = lastEpsEstimate ?: fallback.lastEpsEstimate,
            lastEpsQuarterLabel = lastEpsQuarterLabel ?: fallback.lastEpsQuarterLabel
        ) ?: fallback.copy(symbol = symbol)
    }

    private fun StockDetails?.withFundamentalsFallback(
        fallback: StockDetails?,
        symbol: String
    ): StockDetails? {
        fallback ?: return this
        return this?.copy(
            totalRevenue = totalRevenue ?: fallback.totalRevenue,
            revenueGrowthPct = revenueGrowthPct ?: fallback.revenueGrowthPct,
            epsGrowthPct = epsGrowthPct ?: fallback.epsGrowthPct,
            grossMarginPct = grossMarginPct ?: fallback.grossMarginPct,
            operatingMarginPct = operatingMarginPct ?: fallback.operatingMarginPct,
            profitMarginPct = profitMarginPct ?: fallback.profitMarginPct,
            freeCashflow = freeCashflow ?: fallback.freeCashflow,
            operatingCashflow = operatingCashflow ?: fallback.operatingCashflow,
            totalCash = totalCash ?: fallback.totalCash,
            totalDebt = totalDebt ?: fallback.totalDebt,
            debtToEquity = debtToEquity ?: fallback.debtToEquity,
            currentRatio = currentRatio ?: fallback.currentRatio,
            trailingPe = trailingPe ?: fallback.trailingPe,
            forwardPe = forwardPe ?: fallback.forwardPe,
            pegRatio = pegRatio ?: fallback.pegRatio,
            priceToBook = priceToBook ?: fallback.priceToBook,
            epsTtm = epsTtm ?: fallback.epsTtm,
            forwardEps = forwardEps ?: fallback.forwardEps,
            dividendYieldPct = dividendYieldPct ?: fallback.dividendYieldPct
        ) ?: fallback.copy(symbol = symbol)
    }

    private fun FinnhubEarningsCalendarEvent.toDetails(
        symbol: String,
        date: LocalDate
    ): StockDetails {
        val eventEpoch = date.atStartOfDay(NAIROBI_ZONE).toEpochSecond()
        val quarterLabel = quarter?.let { q ->
            year?.let { "Q$q $it" } ?: "Q$q"
        }
        val hasReported = date.isBefore(LocalDate.now(NAIROBI_ZONE)) && epsActual != null

        return StockDetails(
            symbol = symbol,
            nextEarningsEpochSeconds = eventEpoch,
            nextEarningsIsEstimate = !hasReported,
            nextEarningsQuarterLabel = quarterLabel,
            lastEpsActual = if (hasReported) epsActual else null,
            lastEpsEstimate = if (hasReported) epsEstimate else null,
            lastEpsQuarterLabel = if (hasReported) quarterLabel else null
        )
    }
}

/**
 * Normalised per-symbol details used by the Dip page UI.
 * Every field is nullable so the UI degrades gracefully when Yahoo is
 * rate-limited.
 */
data class StockDetails(
    val symbol: String,
    // Next earnings event
    val nextEarningsEpochSeconds: Long? = null,
    val nextEarningsIsEstimate: Boolean? = null,
    val nextEarningsQuarterLabel: String? = null,
    // Last reported quarterly EPS surprise
    val lastEpsActual: Double? = null,
    val lastEpsEstimate: Double? = null,
    val lastEpsQuarterLabel: String? = null,
    // Analyst price target
    val analystTargetMean: Double? = null,
    val analystTargetHigh: Double? = null,
    val analystTargetLow: Double? = null,
    val analystOpinionsCount: Long? = null,
    val analystRecommendation: String? = null,
    // Core fundamentals
    val totalRevenue: Double? = null,
    val revenueGrowthPct: Double? = null,
    val epsGrowthPct: Double? = null,
    val grossMarginPct: Double? = null,
    val operatingMarginPct: Double? = null,
    val profitMarginPct: Double? = null,
    val freeCashflow: Double? = null,
    val operatingCashflow: Double? = null,
    val totalCash: Double? = null,
    val totalDebt: Double? = null,
    val debtToEquity: Double? = null,
    val currentRatio: Double? = null,
    val trailingPe: Double? = null,
    val forwardPe: Double? = null,
    val pegRatio: Double? = null,
    val priceToBook: Double? = null,
    val epsTtm: Double? = null,
    val forwardEps: Double? = null,
    val dividendYieldPct: Double? = null,
    // 52-week range + trend baselines
    val fiftyTwoWeekHigh: Double? = null,
    val fiftyTwoWeekLow: Double? = null,
    val twoHundredDayAverage: Double? = null,
    val fiftyDayAverage: Double? = null,
    // Volume signals
    val averageVolume: Long? = null,
    val averageVolume10Day: Long? = null,
    val currentVolume: Long? = null
) {
    /** Current price vs analyst mean target as a +/- % (positive = upside). */
    fun upsideToTargetPct(currentPrice: Double?): Double? {
        if (currentPrice == null || currentPrice <= 0.0) return null
        val tgt = analystTargetMean ?: return null
        return (tgt - currentPrice) / currentPrice * 100.0
    }

    /** Last quarter's EPS surprise as a % of estimate. Positive = beat. */
    fun epsSurprisePct(): Double? {
        val a = lastEpsActual ?: return null
        val e = lastEpsEstimate ?: return null
        if (e == 0.0) return null
        return (a - e) / kotlin.math.abs(e) * 100.0
    }

    /** Where price sits in the 52w range, 0.0 = at low, 1.0 = at high. */
    fun positionInRange(currentPrice: Double?): Float? {
        val price = currentPrice ?: return null
        val hi = fiftyTwoWeekHigh ?: return null
        val lo = fiftyTwoWeekLow ?: return null
        if (hi <= lo) return null
        return ((price - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
    }

    /** % above (positive) or below (negative) the 200-day moving average. */
    fun pctVs200dMa(currentPrice: Double?): Double? {
        val price = currentPrice ?: return null
        val ma = twoHundredDayAverage ?: return null
        if (ma <= 0.0) return null
        return (price - ma) / ma * 100.0
    }

    /**
     * Ratio of current/recent vs typical volume. `> 1.5` is a meaningful
     * unusual-volume signal. Returns null if either side is missing.
     */
    fun volumeSpikeRatio(): Double? {
        val recent = (currentVolume ?: averageVolume10Day) ?: return null
        val avg = averageVolume ?: return null
        if (avg <= 0L) return null
        return recent.toDouble() / avg.toDouble()
    }

    fun needsFundamentalFallback(): Boolean =
        totalRevenue == null &&
            revenueGrowthPct == null &&
            epsGrowthPct == null &&
            profitMarginPct == null &&
            freeCashflow == null &&
            totalDebt == null &&
            debtToEquity == null &&
            trailingPe == null &&
            epsTtm == null
}

private fun YahooQuoteSummaryResult.toDetails(symbol: String): StockDetails {
    val cal = calendarEvents?.earnings
    val chart = earnings?.earningsChart
    val lastQ = chart?.quarterly?.lastOrNull()
    val fd = financialData
    val sd = summaryDetail
    val stats = defaultKeyStatistics

    return StockDetails(
        symbol = symbol,
        nextEarningsEpochSeconds = cal?.earningsDate?.firstOrNull()?.raw,
        nextEarningsIsEstimate = cal?.isEarningsDateEstimate,
        nextEarningsQuarterLabel = chart?.let {
            normaliseQuarterLabel(it.currentQuarterEstimateDate, it.currentQuarterEstimateYear)
        },
        lastEpsActual = lastQ?.actual?.raw,
        lastEpsEstimate = lastQ?.estimate?.raw,
        lastEpsQuarterLabel = lastQ?.date,
        analystTargetMean = fd?.targetMeanPrice?.raw,
        analystTargetHigh = fd?.targetHighPrice?.raw,
        analystTargetLow = fd?.targetLowPrice?.raw,
        analystOpinionsCount = fd?.numberOfAnalystOpinions?.raw,
        analystRecommendation = fd?.recommendationKey,
        totalRevenue = fd?.totalRevenue?.raw,
        revenueGrowthPct = fd?.revenueGrowth?.raw?.asRatioPercent(),
        epsGrowthPct = fd?.earningsGrowth?.raw?.asRatioPercent(),
        grossMarginPct = fd?.grossMargins?.raw?.asRatioPercent(),
        operatingMarginPct = fd?.operatingMargins?.raw?.asRatioPercent(),
        profitMarginPct = fd?.profitMargins?.raw?.asRatioPercent(),
        freeCashflow = fd?.freeCashflow?.raw,
        operatingCashflow = fd?.operatingCashflow?.raw,
        totalCash = fd?.totalCash?.raw,
        totalDebt = fd?.totalDebt?.raw,
        debtToEquity = fd?.debtToEquity?.raw,
        currentRatio = fd?.currentRatio?.raw,
        trailingPe = sd?.trailingPE?.raw,
        forwardPe = sd?.forwardPE?.raw,
        pegRatio = stats?.pegRatio?.raw,
        priceToBook = stats?.priceToBook?.raw,
        epsTtm = stats?.trailingEps?.raw,
        forwardEps = stats?.forwardEps?.raw,
        dividendYieldPct = sd?.dividendYield?.raw?.asRatioPercent(),
        fiftyTwoWeekHigh = sd?.fiftyTwoWeekHigh?.raw,
        fiftyTwoWeekLow = sd?.fiftyTwoWeekLow?.raw,
        twoHundredDayAverage = sd?.twoHundredDayAverage?.raw,
        fiftyDayAverage = sd?.fiftyDayAverage?.raw,
        averageVolume = sd?.averageVolume?.raw,
        averageVolume10Day = sd?.averageDailyVolume10Day?.raw,
        currentVolume = sd?.regularMarketVolume?.raw ?: sd?.volume?.raw
    )
}

private fun AlphaCompanyOverview.toDetails(symbol: String): StockDetails =
    StockDetails(
        symbol = this.symbol?.takeIf { it.isNotBlank() } ?: symbol,
        totalRevenue = revenueTtm.toFiniteDoubleOrNull(),
        revenueGrowthPct = quarterlyRevenueGrowthYoY.toRatioPercentOrNull(),
        epsGrowthPct = quarterlyEarningsGrowthYoY.toRatioPercentOrNull(),
        operatingMarginPct = operatingMargin.toRatioPercentOrNull(),
        profitMarginPct = profitMargin.toRatioPercentOrNull(),
        debtToEquity = debtToEquity.toFiniteDoubleOrNull(),
        trailingPe = peRatio.toFiniteDoubleOrNull(),
        pegRatio = pegRatio.toFiniteDoubleOrNull(),
        priceToBook = priceToBookRatio.toFiniteDoubleOrNull(),
        epsTtm = eps.toFiniteDoubleOrNull(),
        fiftyTwoWeekHigh = high52w.toFiniteDoubleOrNull(),
        fiftyTwoWeekLow = low52w.toFiniteDoubleOrNull(),
        twoHundredDayAverage = ma200.toFiniteDoubleOrNull()
    )

private fun Double.asRatioPercent(): Double = this * 100.0

private fun String?.toRatioPercentOrNull(): Double? =
    toFiniteDoubleOrNull()?.let { it * 100.0 }

private fun String?.toFiniteDoubleOrNull(): Double? {
    val value = this?.takeUnless { it.equals("None", ignoreCase = true) }
        ?.takeUnless { it == "-" }
        ?.toDoubleOrNull()
    return value?.takeIf { it.isFinite() }
}

private fun normaliseQuarterLabel(rawQuarter: String?, year: Int?): String? {
    val quarter = rawQuarter
        ?.trim()
        ?.uppercase()
        ?.let { value ->
            val qFirst = Regex("""^Q([1-4])""").find(value)?.groupValues?.getOrNull(1)
            val qLast = Regex("""^([1-4])Q""").find(value)?.groupValues?.getOrNull(1)
            (qFirst ?: qLast)?.let { "Q$it" }
        }

    return when {
        quarter != null && year != null -> "$quarter $year"
        quarter != null -> quarter
        year != null -> year.toString()
        else -> null
    }
}
