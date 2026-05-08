package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.db.DipFinderDao
import com.stockwatchdog.app.data.db.entities.DipFinderResultEntity
import com.stockwatchdog.app.data.db.entities.DipFinderWatchlistEntity
import com.stockwatchdog.app.data.prefs.SettingsRepository
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.DipAnalysis
import com.stockwatchdog.app.domain.DipConfidence
import com.stockwatchdog.app.domain.DipLabel
import com.stockwatchdog.app.domain.DipScorer
import com.stockwatchdog.app.domain.DipSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Coordinates everything the Dip Finder needs:
 *  - 1-year daily price series  (Yahoo  primary, Twelve Data fallback)
 *  - company fundamentals       (Alpha Vantage OVERVIEW; best-effort)
 *  - dip scoring                (DipScorer)
 *  - 24h Room-cached results    (DipFinderDao)
 *  - user watchlist of tickers  (DipFinderDao)
 *
 * The Auto Dip Finder universe is a small, hardcoded list of widely-followed
 * tickers so the free-tier API budget is predictable. Users can add more
 * tickers to their watchlist for personal tracking.
 *
 * Caching is the key trick: every analysis is upserted into Room with a
 * timestamp, and re-runs within [DipFinderResultEntity.DEFAULT_TTL_MS] just
 * return the cached row. This keeps the screen instant on cold start and
 * keeps the user well within the free-tier daily caps.
 */
class DipFinderRepository(
    private val twelveData: TwelveDataApi,
    private val alphaVantage: AlphaVantageApi,
    private val yahooFinance: YahooFinanceApi,
    private val market: MarketDataRepository,
    private val dao: DipFinderDao,
    private val settings: SettingsRepository,
    private val cooldown: ProviderCooldown
) {

    /**
     * Curated starter universe of well-known US large-caps. Kept small on
     * purpose: ~8 symbols means the auto pass costs ~8 Yahoo calls (free
     * with no key) and only triggers Alpha Vantage OVERVIEW when the
     * cache is stale.
     */
    val autoUniverse: List<String> = listOf(
        "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX"
    )

    // ---- Observers used by the ViewModel ----------------------------------

    fun observeAllResults(): Flow<List<DipFinderResultEntity>> = dao.observeResults()

    fun observeWatchlist(): Flow<List<String>> =
        dao.observeWatchlist().map { list -> list.map { it.symbol } }

    // ---- Watchlist mutations ---------------------------------------------

    suspend fun addToWatchlist(symbol: String) {
        val s = symbol.trim().uppercase()
        if (s.isBlank()) return
        dao.addToWatchlist(DipFinderWatchlistEntity(symbol = s))
    }

    suspend fun removeFromWatchlist(symbol: String) {
        dao.removeFromWatchlist(symbol.trim().uppercase())
        // Also clear any cached analysis so the row disappears from the UI.
        dao.deleteResult(symbol.trim().uppercase())
    }

    // ---- Analysis API ----------------------------------------------------

    /**
     * Re-analyse [symbol] unless the cached row is still fresh (and
     * [forceRefresh] is false). The freshly-computed analysis is upserted
     * into Room so the UI's [observeAllResults] flow re-emits automatically.
     *
     * Errors are absorbed: if absolutely no data could be fetched, the
     * analysis is recorded with [DipConfidence.LOW] and a friendly reason
     * so the row still appears in the UI rather than vanishing.
     */
    suspend fun refreshSymbol(
        symbol: String,
        forceRefresh: Boolean = false
    ): DipAnalysis = withContext(Dispatchers.IO) {
        val sym = symbol.trim().uppercase()
        if (!forceRefresh) {
            val cached = dao.getResult(sym)
            if (cached != null && isFresh(cached)) {
                return@withContext cached.toAnalysis()
            }
        }

        val signals = gatherSignals(sym)
        val name = signals.first
        val analysis = DipScorer.analyze(
            symbol = sym,
            name = name,
            signals = signals.second,
            computedAtMillis = System.currentTimeMillis()
        )
        dao.upsertResult(analysis.toEntity())
        analysis
    }

    /**
     * Refresh a batch of tickers sequentially so we stay polite to free-tier
     * API rate limits (Twelve Data: 8/min, Alpha Vantage: ~5/min). The
     * underlying [ProviderCooldown] still protects every call, so a single
     * limit hit just degrades that ticker's confidence gracefully.
     */
    suspend fun refreshMany(
        symbols: List<String>,
        forceRefresh: Boolean = false
    ): List<DipAnalysis> = withContext(Dispatchers.IO) {
        symbols.distinct().map { refreshSymbol(it, forceRefresh) }
    }

    /**
     * Refresh the auto universe + every watchlist symbol. Used by the
     * "Refresh all" action in the UI.
     */
    suspend fun refreshAll(forceRefresh: Boolean = false): List<DipAnalysis> {
        val watch = dao.watchlistSymbols()
        val all = (autoUniverse + watch).distinct()
        return refreshMany(all, forceRefresh)
    }

    // ---- Internals -------------------------------------------------------

    private fun isFresh(row: DipFinderResultEntity): Boolean {
        val age = System.currentTimeMillis() - row.computedAtMillis
        return age in 0..DipFinderResultEntity.DEFAULT_TTL_MS
    }

    /**
     * Best-effort fetch of every signal we know how to compute. Returns the
     * resolved company name (may be null) plus the [DipSignals]. Each sub-
     * fetch is wrapped in runCatching so a single failing provider doesn't
     * sink the whole analysis.
     */
    private suspend fun gatherSignals(symbol: String): Pair<String?, DipSignals> {
        // 1. Current price (reuses MarketDataRepository's full provider chain).
        val quote = (market.getQuote(symbol) as? DataResult.Success)?.value
        val currentPrice = quote?.price
        var name: String? = quote?.name

        // 2. 1-year daily series → 52w high / 52w low / MA200.
        val closes = fetchYearSeries(symbol)
        val high52w = closes.maxOrNull()
        val low52w = closes.minOrNull()
        val ma200 = DipScorer.simpleMovingAverage200(closes)

        // 3. Company fundamentals (best-effort; AV free tier is small).
        val overview = fetchOverview(symbol)
        if (name.isNullOrBlank()) name = overview?.name

        // 4. Resolve 52w high/low if Yahoo didn't give us one (use AV's value).
        val effectiveHigh = high52w ?: overview?.high52w?.toDoubleOrNull()
        val effectiveLow = low52w ?: overview?.low52w?.toDoubleOrNull()
        val effectiveMa200 = ma200 ?: overview?.ma200?.toDoubleOrNull()

        val pctFromHigh = DipScorer.pctFromHigh(currentPrice, effectiveHigh)
        val pctFromLow = DipScorer.pctFromLow(currentPrice, effectiveLow)

        return name to DipSignals(
            currentPrice = currentPrice,
            high52w = effectiveHigh,
            low52w = effectiveLow,
            ma200 = effectiveMa200,
            pctFromHigh = pctFromHigh,
            pctFromLow = pctFromLow,
            revenueGrowthYoYPct = overview?.quarterlyRevenueGrowthYoY
                ?.toDoubleOrNull()?.let { it * 100.0 }, // AV returns 0.124 → 12.4%
            profitMarginPct = overview?.profitMargin
                ?.toDoubleOrNull()?.let { it * 100.0 },
            debtToEquity = overview?.debtToEquity?.toDoubleOrNull(),
            epsTtm = overview?.eps?.toDoubleOrNull()
        )
    }

    /**
     * Pull ~1 year of daily closes. Yahoo first because it's free and
     * unkeyed; Twelve Data is the fallback when Yahoo blocks us.
     * Returns an empty list if both fail — callers tolerate that.
     */
    private suspend fun fetchYearSeries(symbol: String): List<Double> {
        // Yahoo: chart?range=1y&interval=1d
        val yahooKey = "YAHOO_DIP_FINDER"
        if (!cooldown.isCoolingDown(yahooKey)) {
            val res = runCatching {
                val env = yahooFinance.chart(symbol, interval = "1d", range = "1y")
                env.chart?.error?.description?.let { throw RuntimeException(it) }
                val r = env.chart?.result?.firstOrNull()
                    ?: throw RuntimeException("No data")
                r.indicators?.quote?.firstOrNull()?.close
                    ?.filterNotNull()
                    ?: emptyList()
            }
            res.onSuccess { return it }
            res.onFailure { cooldown.trip(yahooKey, ProviderCooldown.PER_MINUTE_CAP_MS) }
        }

        // Twelve Data fallback: 1day interval, ~252 outputsize
        val s = settings.settings.first()
        if (s.twelveDataKey.isBlank()) return emptyList()
        val tdKey = "TWELVE_DATA_DIP_FINDER"
        if (cooldown.isCoolingDown(tdKey)) return emptyList()
        return runCatching {
            val r = twelveData.timeSeries(symbol, "1day", 252, s.twelveDataKey)
            if (r.status == "error") throw RuntimeException(r.message ?: "Twelve Data error")
            r.values.asReversed().mapNotNull { it.close?.toDoubleOrNull() }
        }.onFailure {
            cooldown.trip(tdKey, ProviderCooldown.PER_MINUTE_CAP_MS)
        }.getOrDefault(emptyList())
    }

    /**
     * Pull Alpha Vantage OVERVIEW. Free tier is heavily limited (~25/day),
     * so failures are silent and just lower the analysis confidence.
     */
    private suspend fun fetchOverview(symbol: String): com.stockwatchdog.app.data.api.models.AlphaCompanyOverview? {
        val s = settings.settings.first()
        if (s.alphaVantageKey.isBlank()) return null
        val avKey = "ALPHA_VANTAGE_OVERVIEW"
        if (cooldown.isCoolingDown(avKey)) return null
        return runCatching {
            val r = alphaVantage.overview(symbol, s.alphaVantageKey)
            // AV returns rate-limit text inside Note/Information when capped.
            if (!r.note.isNullOrBlank() || !r.information.isNullOrBlank()) {
                cooldown.trip(avKey, ProviderCooldown.PER_DAY_CAP_MS)
                return null
            }
            r
        }.onFailure {
            cooldown.trip(avKey, ProviderCooldown.PER_MINUTE_CAP_MS)
        }.getOrNull()
    }

    // ---- Mapping helpers -------------------------------------------------

    private fun DipAnalysis.toEntity(): DipFinderResultEntity = DipFinderResultEntity(
        symbol = symbol,
        name = name,
        currentPrice = currentPrice,
        high52w = signals.high52w,
        low52w = signals.low52w,
        ma200 = signals.ma200,
        pctFromHigh = pctFromHigh,
        pctFromLow = signals.pctFromLow,
        nearLow = nearLow,
        revenueGrowthYoYPct = signals.revenueGrowthYoYPct,
        profitMarginPct = signals.profitMarginPct,
        debtToEquity = signals.debtToEquity,
        epsTtm = signals.epsTtm,
        score = score,
        label = label.name,
        confidence = confidence.name,
        reason = reason,
        computedAtMillis = computedAtMillis
    )
}

/**
 * Maps a cached row back to the in-memory analysis the UI uses. Tolerant
 * of unknown enum names so old caches don't crash after a label rename.
 */
fun DipFinderResultEntity.toAnalysis(): DipAnalysis = DipAnalysis(
    symbol = symbol,
    name = name,
    currentPrice = currentPrice,
    pctFromHigh = pctFromHigh,
    nearLow = nearLow,
    score = score,
    label = runCatching { DipLabel.valueOf(label) }.getOrDefault(DipLabel.NOT_IN_DIP),
    confidence = runCatching { DipConfidence.valueOf(confidence) }.getOrDefault(DipConfidence.LOW),
    reason = reason,
    computedAtMillis = computedAtMillis,
    signals = DipSignals(
        currentPrice = currentPrice,
        high52w = high52w,
        low52w = low52w,
        ma200 = ma200,
        pctFromHigh = pctFromHigh,
        pctFromLow = pctFromLow,
        revenueGrowthYoYPct = revenueGrowthYoYPct,
        profitMarginPct = profitMarginPct,
        debtToEquity = debtToEquity,
        epsTtm = epsTtm
    )
)
