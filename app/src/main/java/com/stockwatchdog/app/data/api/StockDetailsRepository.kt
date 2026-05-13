package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.FinnhubEarningsCalendarEvent
import com.stockwatchdog.app.data.api.models.AlphaCompanyOverview
import com.stockwatchdog.app.data.api.models.EdgarCompanyFacts
import com.stockwatchdog.app.data.api.models.EdgarConcept
import com.stockwatchdog.app.data.api.models.EdgarFact
import com.stockwatchdog.app.data.api.models.FinnhubRecommendationTrend
import com.stockwatchdog.app.data.api.models.FmpBalanceSheet
import com.stockwatchdog.app.data.api.models.FmpCashFlowStatement
import com.stockwatchdog.app.data.api.models.FmpEarningsEvent
import com.stockwatchdog.app.data.api.models.FmpIncomeStatement
import com.stockwatchdog.app.data.api.models.FmpQuote
import com.stockwatchdog.app.data.api.models.YahooQuoteSummaryResult
import com.stockwatchdog.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    private val fmp: FmpApi,
    private val edgar: EdgarApi,
    private val settings: SettingsRepository,
    private val cooldown: ProviderCooldown
) {

    /** Result TTL: 6 hours. Earnings/analyst data is daily-ish. */
    private val ttlMs: Long = 6 * 60 * 60 * 1000L
    private val edgarTtlMs: Long = 12 * 60 * 60 * 1000L
    private val edgarTickerMapTtlMs: Long = 24 * 60 * 60 * 1000L

    private val cache: MutableMap<String, CachedDetails> = HashMap()
    private val lock = Mutex()
    private val cikLock = Mutex()
    private val tickerToCik: MutableMap<String, String> = HashMap()
    private var cikMapLoadedAtMs: Long = 0L
    private val edgarCache: MutableMap<String, CachedEdgarDetails> = HashMap()
    private val edgarLock = Mutex()
    private val edgarThrottleLock = Mutex()
    private var lastEdgarRequestAtMs: Long = 0L

    private data class CachedDetails(
        val details: StockDetails,
        val fetchedAtMs: Long
    )

    private data class CachedEdgarDetails(
        val details: StockDetails?,
        val fetchedAtMs: Long
    )

    /**
     * Get details for [symbol]. Returns `null` if the upstream is
     * unavailable (rate-limited, unauthorized, network error). Callers
     * should render UI gracefully without these fields.
     */
    suspend fun get(
        symbol: String,
        forceRefresh: Boolean = false,
        includeFmp: Boolean = false
    ): StockDetails? {
        val s = symbol.trim().uppercase()
        if (s.isBlank()) return null

        if (!forceRefresh) {
            lock.withLock {
                val hit = cache[s]
                if (
                    hit != null &&
                    System.currentTimeMillis() - hit.fetchedAtMs < ttlMs &&
                    (!includeFmp || hit.details.hasPreferredFinancialSource())
                ) {
                    return hit.details
                }
            }
        }

        val fmpDetails = if (includeFmp) fetchFmpDetails(s) else null
        val edgarDetails = if (includeFmp) fetchEdgarDetails(s) else null

        val yahooDetails = if (cooldown.isCoolingDown(YAHOO_SUMMARY_KEY)) {
            cachedOrNull(s)
        } else {
            fetchYahooDetails(s) ?: cachedOrNull(s)
        }

        val mergedDetails = edgarDetails
            .withDetailsFallback(fmpDetails, s)
            .withDetailsFallback(yahooDetails, s)

        val withEarnings = if (
            mergedDetails == null ||
            mergedDetails.nextEarningsEpochSeconds == null ||
            mergedDetails.nextEarningsQuarterLabel == null
        ) {
            mergedDetails.withDetailsFallback(fetchFinnhubEarnings(s), s)
        } else {
            mergedDetails
        }

        val withRecommendation = if (includeFmp) {
            withEarnings.withDetailsFallback(fetchFinnhubRecommendation(s), s)
        } else {
            withEarnings
        }

        val fresh = withRecommendation
            .withDetailsFallback(fetchAlphaOverviewIfNeeded(s, withEarnings), s)
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
        forceRefresh: Boolean = false,
        includeFmp: Boolean = false
    ): Map<String, StockDetails> {
        if (symbols.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, StockDetails>()
        for (s in symbols.distinct()) {
            val d = get(s, forceRefresh = forceRefresh, includeFmp = includeFmp) ?: continue
            out[s] = d
        }
        return out
    }

    private suspend fun cachedOrNull(s: String): StockDetails? = lock.withLock {
        cache[s]?.details
    }

    private companion object {
        const val FMP_DETAILS_KEY = "FMP_DETAILS"
        const val EDGAR_DETAILS_KEY = "EDGAR_DETAILS"
        const val YAHOO_SUMMARY_KEY = "YAHOO_QUOTE_SUMMARY"
        const val FINNHUB_EARNINGS_KEY = "FINNHUB_EARNINGS"
        const val FINNHUB_RECOMMENDATION_KEY = "FINNHUB_RECOMMENDATION"
        const val ALPHA_OVERVIEW_KEY = "ALPHA_OVERVIEW_DETAILS"
        val NAIROBI_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
        val FINNHUB_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        const val EDGAR_MIN_REQUEST_INTERVAL_MS: Long = 600L
    }

    private suspend fun fetchFmpDetails(symbol: String): StockDetails? {
        if (cooldown.isCoolingDown(FMP_DETAILS_KEY)) return null
        val apiKey = settings.settings.first().fmpKey
        if (apiKey.isBlank()) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val earnings = runCatching {
                    fmp.earnings(symbol = symbol, limit = 8, apiKey = apiKey)
                }.getOrElse { emptyList() }
                val income = fmp.incomeStatements(
                    symbol = symbol,
                    period = "quarter",
                    limit = 6,
                    apiKey = apiKey
                )
                val balance = runCatching {
                    fmp.balanceSheets(symbol = symbol, period = "quarter", limit = 1, apiKey = apiKey)
                }.getOrElse { emptyList() }
                val cashFlow = runCatching {
                    fmp.cashFlowStatements(symbol = symbol, period = "quarter", limit = 1, apiKey = apiKey)
                }.getOrElse { emptyList() }
                val quote = runCatching {
                    fmp.quote(symbol = symbol, apiKey = apiKey).firstOrNull()
                }.getOrNull()

                val details = buildFmpDetails(
                    symbol = symbol,
                    earnings = earnings,
                    income = income,
                    balance = balance.firstOrNull(),
                    cashFlow = cashFlow.firstOrNull(),
                    quote = quote
                )
                cooldown.clear(FMP_DETAILS_KEY)
                details
            }.onFailure { failure ->
                val code = (failure as? HttpException)?.code()
                val msg = failure.message.orEmpty().lowercase()
                when {
                    code == 429 || "rate" in msg || "limit" in msg -> {
                        cooldown.trip(FMP_DETAILS_KEY, ProviderCooldown.PER_DAY_CAP_MS)
                    }
                    code == 401 -> {
                        cooldown.trip(FMP_DETAILS_KEY, ProviderCooldown.PER_DAY_CAP_MS)
                    }
                    code == 402 || code == 403 -> {
                        // Some tickers/endpoints are restricted on the free plan.
                        // Keep FMP available for the next symbol and let Yahoo/SEC/Alpha fill this one.
                    }
                    else -> cooldown.trip(FMP_DETAILS_KEY, ProviderCooldown.PER_MINUTE_CAP_MS)
                }
            }.getOrNull()
        }
    }

    private suspend fun fetchEdgarDetails(symbol: String): StockDetails? {
        if (cooldown.isCoolingDown(EDGAR_DETAILS_KEY)) return null
        edgarLock.withLock {
            val hit = edgarCache[symbol]
            if (hit != null && System.currentTimeMillis() - hit.fetchedAtMs < edgarTtlMs) {
                return hit.details
            }
        }

        val cik = resolveEdgarCik(symbol) ?: return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val facts = edgarRequest { edgar.companyFacts(cik) }
                buildEdgarDetails(symbol = symbol, facts = facts).also { details ->
                    edgarLock.withLock {
                        edgarCache[symbol] = CachedEdgarDetails(details, System.currentTimeMillis())
                    }
                }
            }.onSuccess {
                cooldown.clear(EDGAR_DETAILS_KEY)
            }.onFailure { failure ->
                val code = (failure as? HttpException)?.code()
                when (code) {
                    404 -> {
                        // Not every ticker has SEC XBRL facts (ETFs, non-US listings).
                        edgarLock.withLock {
                            edgarCache[symbol] = CachedEdgarDetails(null, System.currentTimeMillis())
                        }
                    }
                    429 -> cooldown.trip(EDGAR_DETAILS_KEY, ProviderCooldown.PER_MINUTE_CAP_MS)
                    else -> cooldown.trip(EDGAR_DETAILS_KEY, ProviderCooldown.PER_MINUTE_CAP_MS)
                }
            }.getOrNull()
        }
    }

    private suspend fun resolveEdgarCik(symbol: String): String? {
        val normalized = symbol.normalizedEdgarTicker()
        cikLock.withLock {
            if (cikMapLoadedAtMs > 0L &&
                System.currentTimeMillis() - cikMapLoadedAtMs >= edgarTickerMapTtlMs
            ) {
                tickerToCik.clear()
                cikMapLoadedAtMs = 0L
            }
            tickerToCik[normalized]?.let { return it }
            if (cikMapLoadedAtMs > 0L) return null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                edgarRequest { edgar.companyTickers() }
                    .values
                    .mapNotNull { company ->
                        val ticker = company.ticker?.normalizedEdgarTicker() ?: return@mapNotNull null
                        val cik = company.cik?.toPaddedCik() ?: return@mapNotNull null
                        ticker to cik
                    }
            }.onFailure {
                cooldown.trip(EDGAR_DETAILS_KEY, ProviderCooldown.PER_MINUTE_CAP_MS)
            }.getOrNull()
        }?.let { entries ->
            cikLock.withLock {
                tickerToCik.clear()
                tickerToCik.putAll(entries)
                cikMapLoadedAtMs = System.currentTimeMillis()
                tickerToCik[normalized]
            }
        }
    }

    private suspend fun <T> edgarRequest(block: suspend () -> T): T {
        val retryDelays = longArrayOf(0L, 2_000L, 10_000L)
        var lastFailure: Throwable? = null
        for (delayMs in retryDelays) {
            if (delayMs > 0L) delay(delayMs)
            throttleEdgarRequest()
            try {
                return block()
            } catch (failure: Throwable) {
                lastFailure = failure
                if (!failure.shouldRetryEdgar()) throw failure
            }
        }
        throw lastFailure ?: IllegalStateException("SEC request failed")
    }

    private suspend fun throttleEdgarRequest() {
        edgarThrottleLock.withLock {
            val now = System.currentTimeMillis()
            val waitMs = EDGAR_MIN_REQUEST_INTERVAL_MS - (now - lastEdgarRequestAtMs)
            if (waitMs > 0L) delay(waitMs)
            lastEdgarRequestAtMs = System.currentTimeMillis()
        }
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

    private suspend fun fetchFinnhubRecommendation(symbol: String): StockDetails? {
        if (cooldown.isCoolingDown(FINNHUB_RECOMMENDATION_KEY)) return null
        val apiKey = settings.settings.first().finnhubKey
        if (apiKey.isBlank()) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                finnhub.recommendationTrends(symbol = symbol, apiKey = apiKey)
                    .firstOrNull()
                    ?.toDetails(symbol)
            }.onSuccess {
                cooldown.clear(FINNHUB_RECOMMENDATION_KEY)
            }.onFailure {
                val msg = it.message.orEmpty().lowercase()
                val duration = if ("429" in msg || "limit" in msg) {
                    ProviderCooldown.PER_MINUTE_CAP_MS
                } else {
                    ProviderCooldown.PER_MINUTE_CAP_MS
                }
                cooldown.trip(FINNHUB_RECOMMENDATION_KEY, duration)
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

    private fun StockDetails?.withDetailsFallback(
        fallback: StockDetails?,
        symbol: String
    ): StockDetails? {
        fallback ?: return this
        val preferAnalystFallback = fallback.hasAnalystVoteCounts()
        return this?.copy(
            financialDataSource = mergeDataSources(financialDataSource, fallback.financialDataSource),
            reportedCurrency = reportedCurrency ?: fallback.reportedCurrency,
            latestFinancialPeriod = latestFinancialPeriod ?: fallback.latestFinancialPeriod,
            nextEarningsEpochSeconds = nextEarningsEpochSeconds ?: fallback.nextEarningsEpochSeconds,
            nextEarningsIsEstimate = nextEarningsIsEstimate ?: fallback.nextEarningsIsEstimate,
            nextEarningsQuarterLabel = nextEarningsQuarterLabel ?: fallback.nextEarningsQuarterLabel,
            lastEpsActual = lastEpsActual ?: fallback.lastEpsActual,
            lastEpsEstimate = lastEpsEstimate ?: fallback.lastEpsEstimate,
            lastEpsQuarterLabel = lastEpsQuarterLabel ?: fallback.lastEpsQuarterLabel,
            lastRevenueActual = lastRevenueActual ?: fallback.lastRevenueActual,
            lastRevenueEstimate = lastRevenueEstimate ?: fallback.lastRevenueEstimate,
            lastRevenueQuarterLabel = lastRevenueQuarterLabel ?: fallback.lastRevenueQuarterLabel,
            analystTargetMean = analystTargetMean ?: fallback.analystTargetMean,
            analystTargetHigh = analystTargetHigh ?: fallback.analystTargetHigh,
            analystTargetLow = analystTargetLow ?: fallback.analystTargetLow,
            analystOpinionsCount = if (preferAnalystFallback) {
                fallback.analystOpinionsCount ?: analystOpinionsCount
            } else {
                analystOpinionsCount ?: fallback.analystOpinionsCount
            },
            analystRecommendation = if (preferAnalystFallback) {
                fallback.analystRecommendation ?: analystRecommendation
            } else {
                analystRecommendation ?: fallback.analystRecommendation
            },
            analystStrongBuyCount = analystStrongBuyCount ?: fallback.analystStrongBuyCount,
            analystBuyCount = analystBuyCount ?: fallback.analystBuyCount,
            analystHoldCount = analystHoldCount ?: fallback.analystHoldCount,
            analystSellCount = analystSellCount ?: fallback.analystSellCount,
            analystStrongSellCount = analystStrongSellCount ?: fallback.analystStrongSellCount,
            analystConsensusPeriod = analystConsensusPeriod ?: fallback.analystConsensusPeriod,
            totalRevenue = totalRevenue ?: fallback.totalRevenue,
            netIncome = netIncome ?: fallback.netIncome,
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
            dividendYieldPct = dividendYieldPct ?: fallback.dividendYieldPct,
            fiftyTwoWeekHigh = fiftyTwoWeekHigh ?: fallback.fiftyTwoWeekHigh,
            fiftyTwoWeekLow = fiftyTwoWeekLow ?: fallback.fiftyTwoWeekLow,
            twoHundredDayAverage = twoHundredDayAverage ?: fallback.twoHundredDayAverage,
            fiftyDayAverage = fiftyDayAverage ?: fallback.fiftyDayAverage,
            averageVolume = averageVolume ?: fallback.averageVolume,
            averageVolume10Day = averageVolume10Day ?: fallback.averageVolume10Day,
            currentVolume = currentVolume ?: fallback.currentVolume
        ) ?: fallback.copy(symbol = symbol)
    }

    private fun FinnhubRecommendationTrend.toDetails(symbol: String): StockDetails {
        val total = listOf(strongBuy, buy, hold, sell, strongSell)
            .mapNotNull { it?.takeIf { value -> value >= 0 } }
            .sum()
            .takeIf { it > 0 }
            ?.toLong()
        return StockDetails(
            symbol = this.symbol?.takeIf { it.isNotBlank() } ?: symbol,
            analystOpinionsCount = total,
            analystRecommendation = consensusKeyFromRecommendationTrend(this),
            analystStrongBuyCount = strongBuy?.takeIf { it >= 0 },
            analystBuyCount = buy?.takeIf { it >= 0 },
            analystHoldCount = hold?.takeIf { it >= 0 },
            analystSellCount = sell?.takeIf { it >= 0 },
            analystStrongSellCount = strongSell?.takeIf { it >= 0 },
            analystConsensusPeriod = period
        )
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

private val EDGAR_REVENUE_TAGS = listOf(
    "RevenueFromContractWithCustomerExcludingAssessedTax",
    "Revenues",
    "SalesRevenueNet",
    "SalesRevenueGoodsNet"
)
private val EDGAR_NET_INCOME_TAGS = listOf("NetIncomeLoss")
private val EDGAR_GROSS_PROFIT_TAGS = listOf("GrossProfit")
private val EDGAR_OPERATING_INCOME_TAGS = listOf("OperatingIncomeLoss")
private val EDGAR_EPS_DILUTED_TAGS = listOf("EarningsPerShareDiluted")
private val EDGAR_EPS_BASIC_TAGS = listOf("EarningsPerShareBasic")
private val EDGAR_OPERATING_CASH_FLOW_TAGS = listOf(
    "NetCashProvidedByUsedInOperatingActivities",
    "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"
)
private val EDGAR_CAPEX_TAGS = listOf(
    "PaymentsToAcquirePropertyPlantAndEquipment",
    "PaymentsToAcquireProductiveAssets",
    "CapitalExpendituresIncurredButNotYetPaid"
)
private val EDGAR_CURRENT_ASSETS_TAGS = listOf("AssetsCurrent")
private val EDGAR_CURRENT_LIABILITIES_TAGS = listOf("LiabilitiesCurrent")
private val EDGAR_CASH_TAGS = listOf(
    "CashAndCashEquivalentsAtCarryingValue",
    "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents",
    "CashCashEquivalentsAndShortTermInvestments",
    "CashAndShortTermInvestments"
)
private val EDGAR_EQUITY_TAGS = listOf(
    "StockholdersEquity",
    "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"
)
private val EDGAR_DEBT_CURRENT_TAGS = listOf(
    "LongTermDebtCurrent",
    "LongTermDebtAndFinanceLeaseObligationsCurrent",
    "DebtCurrent",
    "ShortTermBorrowings",
    "ShortTermDebt"
)
private val EDGAR_DEBT_NONCURRENT_TAGS = listOf(
    "LongTermDebtNoncurrent",
    "LongTermDebtAndFinanceLeaseObligationsNoncurrent",
    "LongTermDebt",
    "LongTermDebtAndFinanceLeaseObligations"
)
private val EDGAR_TOTAL_DEBT_TAGS = listOf(
    "LongTermDebtAndFinanceLeaseObligations",
    "LongTermDebt",
    "Debt"
)
private val EDGAR_DURATION_FRAME = Regex("""^CY\d{4}(Q[1-4])?$""")
private val EDGAR_INSTANT_FRAME = Regex("""^CY\d{4}(Q[1-4])?I$""")
private val EDGAR_PERIOD_FRAME = Regex("""^CY(\d{4})(Q[1-4])?$""")
private val EDGAR_FILED_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

private fun consensusKeyFromRecommendationTrend(trend: FinnhubRecommendationTrend): String? {
    val buckets = listOf(
        "strong_buy" to (trend.strongBuy ?: 0),
        "buy" to (trend.buy ?: 0),
        "hold" to (trend.hold ?: 0),
        "sell" to (trend.sell ?: 0),
        "strong_sell" to (trend.strongSell ?: 0)
    )
    val total = buckets.sumOf { it.second }
    if (total <= 0) return null
    return buckets.maxByOrNull { it.second }?.first
}

private fun buildFmpDetails(
    symbol: String,
    earnings: List<FmpEarningsEvent>,
    income: List<FmpIncomeStatement>,
    balance: FmpBalanceSheet?,
    cashFlow: FmpCashFlowStatement?,
    quote: FmpQuote?
): StockDetails? {
    val latestIncome = income.firstOrNull()
    val comparisonIncome = latestIncome?.let { latest ->
        income.drop(1).firstOrNull {
            it.period == latest.period &&
                it.fiscalYear?.toIntOrNull() == latest.fiscalYear?.toIntOrNull()?.minus(1)
        }
    }
    val latestPeriod = latestIncome?.periodLabel()
        ?: balance?.periodLabel()
        ?: cashFlow?.periodLabel()

    val today = LocalDate.now(ZoneId.of("Africa/Nairobi"))
    val datedEarnings = earnings.mapNotNull { event ->
        val rawDate = event.date ?: return@mapNotNull null
        val date = runCatching { LocalDate.parse(rawDate, DateTimeFormatter.ISO_LOCAL_DATE) }
            .getOrNull() ?: return@mapNotNull null
        event to date
    }
    val nextEvent = datedEarnings
        .filter { !it.second.isBefore(today) }
        .minByOrNull { it.second }
    val lastEvent = datedEarnings
        .filter { it.second.isBefore(today) || it.second.isEqual(today) }
        .filter { it.first.epsActual != null || it.first.revenueActual != null }
        .maxByOrNull { it.second }

    val epsTtm = income.take(4)
        .mapNotNull { it.epsDiluted ?: it.eps }
        .takeIf { it.isNotEmpty() }
        ?.sum()
        ?.takeIf { it.isFinite() }
    val trailingPe = quote?.price?.let { price ->
        epsTtm?.takeIf { it > 0.0 }?.let { price / it }
    }
    val totalEquity = balance?.totalStockholdersEquity ?: balance?.totalEquity

    val details = StockDetails(
        symbol = symbol,
        financialDataSource = "FMP",
        reportedCurrency = latestIncome?.reportedCurrency ?: balance?.reportedCurrency ?: cashFlow?.reportedCurrency,
        latestFinancialPeriod = latestPeriod,
        nextEarningsEpochSeconds = nextEvent?.second?.atStartOfDay(ZoneId.of("Africa/Nairobi"))?.toEpochSecond(),
        nextEarningsIsEstimate = nextEvent?.let { it.first.epsActual == null },
        lastEpsActual = lastEvent?.first?.epsActual,
        lastEpsEstimate = lastEvent?.first?.epsEstimated,
        lastEpsQuarterLabel = latestPeriod,
        lastRevenueActual = lastEvent?.first?.revenueActual,
        lastRevenueEstimate = lastEvent?.first?.revenueEstimated,
        lastRevenueQuarterLabel = latestPeriod,
        totalRevenue = latestIncome?.revenue,
        netIncome = latestIncome?.netIncome,
        revenueGrowthPct = percentChange(latestIncome?.revenue, comparisonIncome?.revenue),
        epsGrowthPct = percentChange(latestIncome?.epsDiluted ?: latestIncome?.eps, comparisonIncome?.epsDiluted ?: comparisonIncome?.eps),
        grossMarginPct = percentOf(latestIncome?.grossProfit, latestIncome?.revenue),
        operatingMarginPct = percentOf(latestIncome?.operatingIncome, latestIncome?.revenue),
        profitMarginPct = percentOf(latestIncome?.netIncome, latestIncome?.revenue),
        freeCashflow = cashFlow?.freeCashFlow,
        operatingCashflow = cashFlow?.operatingCashFlow,
        totalCash = balance?.cashAndShortTermInvestments ?: balance?.cashAndCashEquivalents,
        totalDebt = balance?.totalDebt,
        debtToEquity = percentOf(balance?.totalDebt, totalEquity),
        currentRatio = ratioOf(balance?.totalCurrentAssets, balance?.totalCurrentLiabilities),
        trailingPe = trailingPe,
        epsTtm = epsTtm,
        fiftyTwoWeekHigh = quote?.yearHigh,
        fiftyTwoWeekLow = quote?.yearLow,
        twoHundredDayAverage = quote?.priceAvg200,
        fiftyDayAverage = quote?.priceAvg50,
        currentVolume = quote?.volume
    )

    return details.takeIf { !it.needsFundamentalFallback() || it.nextEarningsEpochSeconds != null }
}

private fun buildEdgarDetails(
    symbol: String,
    facts: EdgarCompanyFacts
): StockDetails? {
    val gaap = facts.facts["us-gaap"].orEmpty()

    val latestRevenue = gaap.latestDurationFact(EDGAR_REVENUE_TAGS, listOf("USD"))
    val revenueComparison = latestRevenue?.let { gaap.comparisonDurationFact(it) }
    val latestNetIncome = gaap.latestDurationFact(EDGAR_NET_INCOME_TAGS, listOf("USD"))
    val latestGrossProfit = gaap.latestDurationFact(EDGAR_GROSS_PROFIT_TAGS, listOf("USD"))
    val latestOperatingIncome = gaap.latestDurationFact(EDGAR_OPERATING_INCOME_TAGS, listOf("USD"))
    val latestEps = gaap.latestDurationFact(EDGAR_EPS_DILUTED_TAGS, listOf("USD/shares"))
        ?: gaap.latestDurationFact(EDGAR_EPS_BASIC_TAGS, listOf("USD/shares"))
    val epsComparison = latestEps?.let { gaap.comparisonDurationFact(it) }
    val latestOperatingCashFlow = gaap.latestDurationFact(EDGAR_OPERATING_CASH_FLOW_TAGS, listOf("USD"))
    val latestCapex = gaap.latestDurationFact(EDGAR_CAPEX_TAGS, listOf("USD"))

    val currentAssets = gaap.latestInstantFact(EDGAR_CURRENT_ASSETS_TAGS, listOf("USD"))
    val currentLiabilities = gaap.latestInstantFact(EDGAR_CURRENT_LIABILITIES_TAGS, listOf("USD"))
    val totalCash = gaap.latestInstantFact(EDGAR_CASH_TAGS, listOf("USD"))
    val equity = gaap.latestInstantFact(EDGAR_EQUITY_TAGS, listOf("USD"))
    val debtCurrent = gaap.latestInstantFact(EDGAR_DEBT_CURRENT_TAGS, listOf("USD"))
    val debtNonCurrent = gaap.latestInstantFact(EDGAR_DEBT_NONCURRENT_TAGS, listOf("USD"))
    val totalDebt = if (debtCurrent != null || debtNonCurrent != null) {
        listOfNotNull(debtCurrent?.fact?.value, debtNonCurrent?.fact?.value)
            .takeIf { it.isNotEmpty() }
            ?.sum()
    } else {
        gaap.latestInstantFact(EDGAR_TOTAL_DEBT_TAGS, listOf("USD"))?.fact?.value
    }

    val revenue = latestRevenue?.fact?.value
    val netIncome = latestNetIncome?.fact?.value
    val operatingCashFlow = latestOperatingCashFlow?.fact?.value
    val capex = latestCapex?.fact?.value
    val freeCashFlow = when {
        operatingCashFlow == null -> null
        capex == null -> null
        capex < 0.0 -> operatingCashFlow + capex
        else -> operatingCashFlow - kotlin.math.abs(capex)
    }
    val period = listOfNotNull(latestRevenue, latestNetIncome, latestEps, latestOperatingCashFlow)
        .maxWithOrNull(edgarPickedFactComparator)
        ?.periodLabel()

    val details = StockDetails(
        symbol = symbol,
        financialDataSource = "SEC EDGAR",
        reportedCurrency = latestRevenue?.currency()
            ?: latestNetIncome?.currency()
            ?: totalCash?.currency(),
        latestFinancialPeriod = period,
        lastEpsActual = latestEps?.fact?.value,
        lastEpsQuarterLabel = period,
        lastRevenueActual = revenue,
        lastRevenueQuarterLabel = period,
        totalRevenue = revenue,
        netIncome = netIncome,
        revenueGrowthPct = percentChange(revenue, revenueComparison?.fact?.value),
        epsGrowthPct = percentChange(latestEps?.fact?.value, epsComparison?.fact?.value),
        grossMarginPct = percentOf(latestGrossProfit?.fact?.value, revenue),
        operatingMarginPct = percentOf(latestOperatingIncome?.fact?.value, revenue),
        profitMarginPct = percentOf(netIncome, revenue),
        freeCashflow = freeCashFlow,
        operatingCashflow = operatingCashFlow,
        totalCash = totalCash?.fact?.value,
        totalDebt = totalDebt,
        debtToEquity = percentOf(totalDebt, equity?.fact?.value),
        currentRatio = ratioOf(currentAssets?.fact?.value, currentLiabilities?.fact?.value),
        epsTtm = latestEps?.takeIf { it.fact.fp?.uppercase(Locale.US) == "FY" }?.fact?.value
    )

    return details.takeIf { !it.needsFundamentalFallback() }
}

/**
 * Normalised per-symbol details used by the Dip page UI.
 * Every field is nullable so the UI degrades gracefully when Yahoo is
 * rate-limited.
 */
data class StockDetails(
    val symbol: String,
    val financialDataSource: String? = null,
    val reportedCurrency: String? = null,
    val latestFinancialPeriod: String? = null,
    // Next earnings event
    val nextEarningsEpochSeconds: Long? = null,
    val nextEarningsIsEstimate: Boolean? = null,
    val nextEarningsQuarterLabel: String? = null,
    // Last reported quarterly EPS surprise
    val lastEpsActual: Double? = null,
    val lastEpsEstimate: Double? = null,
    val lastEpsQuarterLabel: String? = null,
    val lastRevenueActual: Double? = null,
    val lastRevenueEstimate: Double? = null,
    val lastRevenueQuarterLabel: String? = null,
    // Analyst price target
    val analystTargetMean: Double? = null,
    val analystTargetHigh: Double? = null,
    val analystTargetLow: Double? = null,
    val analystOpinionsCount: Long? = null,
    val analystRecommendation: String? = null,
    val analystStrongBuyCount: Int? = null,
    val analystBuyCount: Int? = null,
    val analystHoldCount: Int? = null,
    val analystSellCount: Int? = null,
    val analystStrongSellCount: Int? = null,
    val analystConsensusPeriod: String? = null,
    // Core fundamentals
    val totalRevenue: Double? = null,
    val netIncome: Double? = null,
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

    /** Last reported revenue surprise as a % of estimate. Positive = beat. */
    fun revenueSurprisePct(): Double? {
        val a = lastRevenueActual ?: return null
        val e = lastRevenueEstimate ?: return null
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
            netIncome == null &&
            revenueGrowthPct == null &&
            epsGrowthPct == null &&
            profitMarginPct == null &&
            freeCashflow == null &&
            totalDebt == null &&
            debtToEquity == null &&
            trailingPe == null &&
            epsTtm == null

    fun hasPreferredFinancialSource(): Boolean =
        financialDataSource?.contains("FMP") == true ||
            financialDataSource?.contains("SEC EDGAR") == true

    fun hasAnalystVoteCounts(): Boolean =
        listOf(
            analystStrongBuyCount,
            analystBuyCount,
            analystHoldCount,
            analystSellCount,
            analystStrongSellCount
        ).sumOf { it ?: 0 } > 0
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
        financialDataSource = "Yahoo",
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
        financialDataSource = "Alpha Vantage",
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

private fun FmpIncomeStatement.periodLabel(): String? =
    when {
        !period.isNullOrBlank() && !fiscalYear.isNullOrBlank() -> "$period $fiscalYear"
        !period.isNullOrBlank() -> period
        !fiscalYear.isNullOrBlank() -> fiscalYear
        else -> null
    }

private fun FmpBalanceSheet.periodLabel(): String? =
    when {
        !period.isNullOrBlank() && !fiscalYear.isNullOrBlank() -> "$period $fiscalYear"
        !period.isNullOrBlank() -> period
        !fiscalYear.isNullOrBlank() -> fiscalYear
        else -> null
    }

private fun FmpCashFlowStatement.periodLabel(): String? =
    when {
        !period.isNullOrBlank() && !fiscalYear.isNullOrBlank() -> "$period $fiscalYear"
        !period.isNullOrBlank() -> period
        !fiscalYear.isNullOrBlank() -> fiscalYear
        else -> null
    }

private fun percentChange(current: Double?, previous: Double?): Double? {
    if (current == null || previous == null || previous == 0.0) return null
    return (current - previous) / kotlin.math.abs(previous) * 100.0
}

private fun percentOf(part: Double?, whole: Double?): Double? {
    if (part == null || whole == null || whole == 0.0) return null
    return part / kotlin.math.abs(whole) * 100.0
}

private fun ratioOf(numerator: Double?, denominator: Double?): Double? {
    if (numerator == null || denominator == null || denominator == 0.0) return null
    return numerator / denominator
}

private fun mergeDataSources(primary: String?, fallback: String?): String? {
    val parts = (primary.orEmpty().split("+") + fallback.orEmpty().split("+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" + ")
}

private fun Throwable.shouldRetryEdgar(): Boolean =
    when (this) {
        is IOException -> true
        is HttpException -> code() == 429 || code() in 500..599
        else -> false
    }

private data class EdgarPickedFact(
    val concept: String,
    val unit: String,
    val fact: EdgarFact
)

private val edgarPickedFactComparator = compareBy<EdgarPickedFact>(
    { it.fact.endDate() ?: LocalDate.MIN },
    { it.fact.filedDate() ?: LocalDate.MIN }
)

private fun Map<String, EdgarConcept>.latestDurationFact(
    tags: List<String>,
    units: List<String>
): EdgarPickedFact? =
    candidateFacts(tags, units)
        .filter { it.fact.isReportForm() && it.fact.isUsableDurationFact() }
        .maxWithOrNull(edgarPickedFactComparator)

private fun Map<String, EdgarConcept>.latestInstantFact(
    tags: List<String>,
    units: List<String>
): EdgarPickedFact? =
    candidateFacts(tags, units)
        .filter { it.fact.isReportForm() && it.fact.isUsableInstantFact() }
        .maxWithOrNull(edgarPickedFactComparator)

private fun Map<String, EdgarConcept>.comparisonDurationFact(current: EdgarPickedFact): EdgarPickedFact? {
    val currentEnd = current.fact.endDate() ?: return null
    val currentFp = current.fact.fp?.uppercase(Locale.US)
    val currentFy = current.fact.fy
    val candidates = candidateFacts(listOf(current.concept), listOf(current.unit))
        .filter { it.fact.isReportForm() && it.fact.isUsableDurationFact() }
        .filter { (it.fact.endDate() ?: LocalDate.MIN).isBefore(currentEnd) }
        .toList()

    val samePeriodPriorYear = candidates
        .filter {
            currentFy != null &&
                it.fact.fy == currentFy - 1 &&
                it.fact.fp?.uppercase(Locale.US) == currentFp
        }
        .maxWithOrNull(edgarPickedFactComparator)

    return samePeriodPriorYear ?: candidates.maxWithOrNull(edgarPickedFactComparator)
}

private fun Map<String, EdgarConcept>.candidateFacts(
    tags: List<String>,
    units: List<String>
): Sequence<EdgarPickedFact> =
    tags.asSequence().flatMap { tag ->
        val concept = this[tag] ?: return@flatMap emptySequence()
        units.asSequence().flatMap { unit ->
            concept.units[unit]
                ?.asSequence()
                ?.mapNotNull { fact ->
                    fact.value
                        ?.takeIf { it.isFinite() }
                        ?.let { EdgarPickedFact(tag, unit, fact) }
                }
                ?: emptySequence()
        }
    }

private fun EdgarFact.isReportForm(): Boolean =
    form?.uppercase(Locale.US) in setOf("10-Q", "10-K", "20-F", "40-F")

private fun EdgarFact.isUsableDurationFact(): Boolean {
    frame?.let { if (EDGAR_DURATION_FRAME.matches(it)) return true }
    val startDate = startDate() ?: return false
    val endDate = endDate() ?: return false
    val days = ChronoUnit.DAYS.between(startDate, endDate)
    return when (fp?.uppercase(Locale.US)) {
        "Q1", "Q2", "Q3", "Q4" -> days in 60..120
        "FY" -> days in 330..400
        else -> days in 60..120 || days in 330..400
    }
}

private fun EdgarFact.isUsableInstantFact(): Boolean {
    frame?.let { if (EDGAR_INSTANT_FRAME.matches(it)) return true }
    return start == null && endDate() != null
}

private fun EdgarPickedFact.periodLabel(): String? {
    val base = fact.frame
        ?.removeSuffix("I")
        ?.let { EDGAR_PERIOD_FRAME.find(it) }
        ?.let { match ->
            val year = match.groupValues.getOrNull(1).orEmpty()
            val quarter = match.groupValues.getOrNull(2).orEmpty()
            if (quarter.isNotBlank()) "$quarter $year" else "FY $year"
        }
        ?: run {
            val fy = fact.fy?.toString()
            val fp = fact.fp?.uppercase(Locale.US)
            when {
                fy != null && fp in setOf("Q1", "Q2", "Q3", "Q4") -> "$fp $fy"
                fy != null && fp == "FY" -> "FY $fy"
                fy != null -> fy
                else -> null
            }
        }
    val filed = fact.filedDate()?.format(EDGAR_FILED_FORMAT)
    return when {
        base != null && filed != null -> "$base filed $filed"
        else -> base
    }
}

private fun EdgarPickedFact.currency(): String? =
    unit.substringBefore("/").takeIf { it.isNotBlank() }

private fun EdgarFact.startDate(): LocalDate? =
    start?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }

private fun EdgarFact.endDate(): LocalDate? =
    end?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }

private fun EdgarFact.filedDate(): LocalDate? =
    filed?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }

private fun String.normalizedEdgarTicker(): String =
    trim()
        .uppercase(Locale.US)
        .substringBefore(":")
        .replace('.', '-')

private fun Long.toPaddedCik(): String = toString().padStart(10, '0')

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
