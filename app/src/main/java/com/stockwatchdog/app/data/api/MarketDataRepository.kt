package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.AlphaCandle
import com.stockwatchdog.app.data.api.models.FinnhubQuote
import com.stockwatchdog.app.data.api.models.TwelveQuote
import com.stockwatchdog.app.data.api.models.YahooChartResult
import com.stockwatchdog.app.data.db.PriceCacheDao
import com.stockwatchdog.app.data.db.entities.PriceCacheEntity
import com.stockwatchdog.app.data.prefs.ApiProvider
import com.stockwatchdog.app.data.prefs.SettingsRepository
import com.stockwatchdog.app.data.prefs.UserSettings
import com.stockwatchdog.app.domain.ChartRange
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.domain.PricePoint
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.domain.SymbolMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Single entry point for market data. Normalizes results from multiple
 * providers (Finnhub, Twelve Data, Yahoo Finance, Alpha Vantage) to the
 * app's domain types, caches quotes in Room for offline viewing, and maps
 * errors to friendly messages.
 *
 * In [ApiProvider.AUTO] mode, each operation walks a provider chain:
 *  - quote:  Twelve Data -> Finnhub -> Yahoo -> Alpha Vantage
 *  - series: Twelve Data -> Yahoo   -> Alpha Vantage
 *  - search: Twelve Data -> Yahoo   -> Finnhub
 *
 * Twelve Data is prioritised because it supports quotes **and** chart series
 * on its free tier, while Finnhub charts are premium-only.
 *
 * Providers that return a rate-limit/auth/quota error are placed in a
 * short cooldown via [ProviderCooldown] so subsequent calls within the
 * cooldown window skip them immediately and the next provider is tried.
 */
class MarketDataRepository(
    private val twelveData: TwelveDataApi,
    private val alphaVantage: AlphaVantageApi,
    private val finnhub: FinnhubApi,
    private val yahooFinance: YahooFinanceApi,
    private val settings: SettingsRepository,
    private val priceCacheDao: PriceCacheDao,
    private val cooldown: ProviderCooldown
) {

    /** Cache TTL for quotes in foreground refreshes. */
    private val quoteTtlMillis: Long = 60_000L

    suspend fun getQuote(symbol: String, forceRefresh: Boolean = false): DataResult<Quote> {
        val cached = priceCacheDao.getOne(symbol)
        if (!forceRefresh && cached != null &&
            System.currentTimeMillis() - cached.fetchedAtMillis < quoteTtlMillis
        ) {
            return DataResult.Success(cached.toDomain())
        }
        return fetchQuote(symbol).also { res ->
            if (res is DataResult.Success) {
                priceCacheDao.upsert(res.value.toEntity())
            }
        }
    }

    suspend fun refreshQuotes(symbols: List<String>): Map<String, DataResult<Quote>> {
        if (symbols.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, DataResult<Quote>>()
        // Sequential to respect free-tier rate limits. The in-repo cooldown
        // keeps rotating providers automatically if any runs out mid-batch.
        for (s in symbols) {
            out[s] = fetchQuote(s).also { res ->
                if (res is DataResult.Success) {
                    priceCacheDao.upsert(res.value.toEntity())
                }
            }
        }
        return out
    }

    suspend fun search(query: String): DataResult<List<SymbolMatch>> = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val chain = searchChain(s)
        runChain(chain, "search") { provider -> searchFrom(provider, query, s) }
    }

    suspend fun getSeries(symbol: String, range: ChartRange): DataResult<List<PricePoint>> =
        withContext(Dispatchers.IO) {
            val s = settings.settings.first()
            val chain = seriesChain(s)
            runChain(chain, "series") { provider -> seriesFrom(provider, symbol, range, s) }
        }

    private suspend fun fetchQuote(symbol: String): DataResult<Quote> = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val chain = quoteChain(s)
        runChain(chain, "quote") { provider -> quoteFrom(provider, symbol, s) }
    }

    // --- Chains ------------------------------------------------------------

    private fun quoteChain(s: UserSettings): List<ApiProvider> = when (s.provider) {
        ApiProvider.AUTO -> listOfNotNull(
            if (s.twelveDataKey.isNotBlank()) ApiProvider.TWELVE_DATA else null,
            if (s.finnhubKey.isNotBlank()) ApiProvider.FINNHUB else null,
            ApiProvider.YAHOO,
            if (s.alphaVantageKey.isNotBlank()) ApiProvider.ALPHA_VANTAGE else null
        )
        else -> listOf(s.provider)
    }

    private fun seriesChain(s: UserSettings): List<ApiProvider> = when (s.provider) {
        ApiProvider.AUTO -> listOfNotNull(
            if (s.twelveDataKey.isNotBlank()) ApiProvider.TWELVE_DATA else null,
            ApiProvider.YAHOO,
            if (s.alphaVantageKey.isNotBlank()) ApiProvider.ALPHA_VANTAGE else null
        )
        ApiProvider.FINNHUB -> listOfNotNull(
            if (s.twelveDataKey.isNotBlank()) ApiProvider.TWELVE_DATA else null,
            ApiProvider.YAHOO,
            if (s.alphaVantageKey.isNotBlank()) ApiProvider.ALPHA_VANTAGE else null
        ) // Finnhub candles are premium, so fall back to the other chart providers.
        else -> listOf(s.provider)
    }

    private fun searchChain(s: UserSettings): List<ApiProvider> = when (s.provider) {
        ApiProvider.AUTO -> listOfNotNull(
            if (s.twelveDataKey.isNotBlank()) ApiProvider.TWELVE_DATA else null,
            ApiProvider.YAHOO,
            if (s.finnhubKey.isNotBlank()) ApiProvider.FINNHUB else null
        )
        else -> listOf(s.provider)
    }

    // --- Chain runner ------------------------------------------------------

    private suspend fun <T> runChain(
        chain: List<ApiProvider>,
        op: String,
        block: suspend (ApiProvider) -> T
    ): DataResult<T> {
        if (chain.isEmpty()) {
            return DataResult.Error(
                "No data provider configured for $op. Add an API key in Settings.",
                retryable = false
            )
        }
        var lastError: DataResult.Error? = null
        var skippedForCooldown = false
        var sawRateLimitOrQuota = false
        for (provider in chain) {
            val key = provider.name
            if (cooldown.isCoolingDown(key)) {
                skippedForCooldown = true
                continue
            }
            val result = runCatchingApi { block(provider) }
            when (result) {
                is DataResult.Success -> {
                    cooldown.clear(key)
                    return result
                }
                is DataResult.Error -> {
                    lastError = result
                    if (looksLikeRateLimit(result.message)) {
                        sawRateLimitOrQuota = true
                        cooldown.trip(key, ProviderCooldown.PER_MINUTE_CAP_MS)
                    } else if (looksLikeDailyCap(result.message)) {
                        sawRateLimitOrQuota = true
                        cooldown.trip(key, ProviderCooldown.PER_DAY_CAP_MS)
                    }
                    // Otherwise just try the next provider in the chain.
                }
            }
        }
        if (op == "series") {
            return when {
                sawRateLimitOrQuota || skippedForCooldown -> DataResult.Error(
                    "Chart data is temporarily unavailable because the free data providers hit a limit. Quotes and positions still work. Try again in a minute or switch chart provider in Settings.",
                    retryable = true
                )
                else -> DataResult.Error(
                    lastError?.message ?: "Chart data is temporarily unavailable right now. Please try again shortly.",
                    retryable = true
                )
            }
        }
        return lastError ?: DataResult.Error("All providers failed for $op.", retryable = true)
    }

    private fun looksLikeRateLimit(msg: String?): Boolean {
        val m = msg?.lowercase() ?: return false
        return "rate limit" in m || "429" in m || "too many requests" in m ||
            ("limit" in m && "minute" in m)
    }

    private fun looksLikeDailyCap(msg: String?): Boolean {
        val m = msg?.lowercase() ?: return false
        return ("daily" in m && "limit" in m) || "quota" in m ||
            ("api" in m && "calls" in m && "day" in m)
    }

    // --- Per-provider quote fetchers --------------------------------------

    private suspend fun quoteFrom(
        provider: ApiProvider,
        symbol: String,
        s: UserSettings
    ): Quote = when (provider) {
        ApiProvider.FINNHUB -> {
            if (s.finnhubKey.isBlank()) apiError("Finnhub key not set")
            val q = finnhub.quote(symbol, s.finnhubKey)
            if (q.current == null || q.current == 0.0) apiError("No quote returned for $symbol")
            q.toDomain(symbol)
        }
        ApiProvider.TWELVE_DATA -> {
            if (s.twelveDataKey.isBlank()) apiError("Twelve Data key not set")
            val q = twelveData.quote(symbol, s.twelveDataKey)
            if (q.status == "error") apiError(q.message ?: "Twelve Data error")
            q.toDomain() ?: apiError("Unexpected empty response from Twelve Data")
        }
        ApiProvider.YAHOO -> {
            val env = yahooFinance.chart(symbol, interval = "1d", range = "5d")
            env.chart?.error?.description?.let { apiError(it) }
            val r = env.chart?.result?.firstOrNull()
                ?: apiError("No quote returned for $symbol")
            r.toQuote(symbol)
        }
        ApiProvider.ALPHA_VANTAGE -> {
            if (s.alphaVantageKey.isBlank()) apiError("Alpha Vantage key not set")
            val env = alphaVantage.globalQuote(symbol, s.alphaVantageKey)
            env.note?.let { apiError(it) }
            env.information?.let { apiError(it) }
            env.errorMessage?.let { apiError(it) }
            val q = env.quote ?: apiError("No quote returned for $symbol")
            val price = q.price?.toDoubleOrNull() ?: apiError("No price for $symbol")
            Quote(
                symbol = q.symbol ?: symbol,
                name = null,
                price = price,
                previousClose = q.previousClose?.toDoubleOrNull(),
                change = q.change?.toDoubleOrNull(),
                percentChange = q.changePercent?.removeSuffix("%")?.toDoubleOrNull(),
                open = q.open?.toDoubleOrNull(),
                high = q.high?.toDoubleOrNull(),
                low = q.low?.toDoubleOrNull(),
                volume = q.volume?.toLongOrNull(),
                marketIsOpen = null,
                currency = null,
                fetchedAtMillis = System.currentTimeMillis()
            )
        }
        ApiProvider.AUTO -> apiError("AUTO is not a concrete provider")
    }

    // --- Per-provider series fetchers -------------------------------------

    private suspend fun seriesFrom(
        provider: ApiProvider,
        symbol: String,
        range: ChartRange,
        s: UserSettings
    ): List<PricePoint> = when (provider) {
        ApiProvider.YAHOO -> {
            val (interval, yrange) = when (range) {
                ChartRange.ONE_DAY -> "5m" to "1d"
                ChartRange.FIVE_DAYS -> "30m" to "5d"
                ChartRange.ONE_MONTH -> "1d" to "1mo"
                ChartRange.THREE_MONTHS -> "1d" to "3mo"
            }
            val env = yahooFinance.chart(symbol, interval, yrange)
            env.chart?.error?.description?.let { apiError(it) }
            val r = env.chart?.result?.firstOrNull()
                ?: apiError("Yahoo returned no data for $symbol")
            val closes = r.indicators?.quote?.firstOrNull()?.close ?: emptyList()
            r.timestamp.zip(closes).mapNotNull { (t, c) ->
                if (c == null) null else PricePoint(t * 1000L, c)
            }
        }
        ApiProvider.TWELVE_DATA -> {
            if (s.twelveDataKey.isBlank()) apiError("Twelve Data key not set")
            val (interval, outputSize) = when (range) {
                ChartRange.ONE_DAY -> "5min" to 78
                ChartRange.FIVE_DAYS -> "30min" to 65
                ChartRange.ONE_MONTH -> "1day" to 30
                ChartRange.THREE_MONTHS -> "1day" to 90
            }
            val r = twelveData.timeSeries(symbol, interval, outputSize, s.twelveDataKey)
            if (r.status == "error") apiError(r.message ?: "Twelve Data error")
            val fmt = if (interval == "1day")
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            else
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            r.values.asReversed().mapNotNull { c ->
                val close = c.close?.toDoubleOrNull() ?: return@mapNotNull null
                val t = runCatching { fmt.parse(c.datetime)?.time }.getOrNull()
                    ?: return@mapNotNull null
                PricePoint(t, close)
            }
        }
        ApiProvider.ALPHA_VANTAGE -> {
            if (s.alphaVantageKey.isBlank()) apiError("Alpha Vantage key not set")
            when (range) {
                ChartRange.ONE_DAY, ChartRange.FIVE_DAYS -> {
                    val interval = if (range == ChartRange.ONE_DAY) "5min" else "30min"
                    val r = alphaVantage.intraday(symbol, interval, "compact", s.alphaVantageKey)
                    r.note?.let { apiError(it) }
                    r.information?.let { apiError(it) }
                    r.errorMessage?.let { apiError(it) }
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    fmt.timeZone = TimeZone.getTimeZone("UTC")
                    alphaSeriesToPoints(r.series(), fmt)
                }
                ChartRange.ONE_MONTH, ChartRange.THREE_MONTHS -> {
                    val r = alphaVantage.daily(symbol, "compact", s.alphaVantageKey)
                    r.note?.let { apiError(it) }
                    r.information?.let { apiError(it) }
                    r.errorMessage?.let { apiError(it) }
                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    fmt.timeZone = TimeZone.getTimeZone("UTC")
                    val points = alphaSeriesToPoints(r.series, fmt)
                    val take = if (range == ChartRange.ONE_MONTH) 22 else 66
                    points.takeLast(take)
                }
            }
        }
        ApiProvider.FINNHUB -> apiError("Finnhub candles are premium on the free tier")
        ApiProvider.AUTO -> apiError("AUTO is not a concrete provider")
    }

    // --- Per-provider symbol search ---------------------------------------

    private suspend fun searchFrom(
        provider: ApiProvider,
        query: String,
        s: UserSettings
    ): List<SymbolMatch> = when (provider) {
        ApiProvider.YAHOO -> {
            val r = yahooFinance.search(query)
            r.quotes.mapNotNull { q ->
                q.symbol?.let {
                    SymbolMatch(
                        symbol = it,
                        name = q.longname ?: q.shortname,
                        exchange = q.exchange,
                        type = q.typeDisp ?: q.quoteType
                    )
                }
            }
        }
        ApiProvider.FINNHUB -> {
            if (s.finnhubKey.isBlank()) apiError("Finnhub key not set")
            val r = finnhub.search(query, s.finnhubKey)
            r.result.map {
                SymbolMatch(
                    symbol = it.displaySymbol ?: it.symbol,
                    name = it.description,
                    exchange = null,
                    type = it.type
                )
            }
        }
        ApiProvider.TWELVE_DATA -> {
            val r = twelveData.search(query)
            r.data.map {
                SymbolMatch(
                    symbol = it.symbol,
                    name = it.instrument_name,
                    exchange = it.exchange,
                    type = it.instrumentType
                )
            }
        }
        ApiProvider.ALPHA_VANTAGE -> {
            if (s.alphaVantageKey.isBlank()) apiError("Alpha Vantage key not set")
            val r = alphaVantage.search(query, s.alphaVantageKey)
            r.note?.let { apiError(it) }
            r.information?.let { apiError(it) }
            r.bestMatches.mapNotNull { m ->
                m.symbol?.let {
                    SymbolMatch(
                        symbol = it,
                        name = m.name,
                        exchange = m.region,
                        type = m.type
                    )
                }
            }
        }
        ApiProvider.AUTO -> apiError("AUTO is not a concrete provider")
    }

    // --- Domain mapping helpers -------------------------------------------

    private fun alphaSeriesToPoints(
        map: Map<String, AlphaCandle>?,
        fmt: SimpleDateFormat
    ): List<PricePoint> {
        if (map.isNullOrEmpty()) return emptyList()
        return map.entries
            .mapNotNull { (k, v) ->
                val close = v.close?.toDoubleOrNull() ?: return@mapNotNull null
                val t = runCatching { fmt.parse(k)?.time }.getOrNull() ?: return@mapNotNull null
                PricePoint(t, close)
            }
            .sortedBy { it.timestampMillis }
    }

    private fun TwelveQuote.toDomain(): Quote? {
        val p = close?.toDoubleOrNull() ?: return null
        return Quote(
            symbol = symbol ?: return null,
            name = name,
            price = p,
            previousClose = previous_close?.toDoubleOrNull(),
            change = change?.toDoubleOrNull(),
            percentChange = percent_change?.toDoubleOrNull(),
            open = open?.toDoubleOrNull(),
            high = high?.toDoubleOrNull(),
            low = low?.toDoubleOrNull(),
            volume = volume?.toLongOrNull(),
            marketIsOpen = is_market_open,
            currency = currency,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    private fun FinnhubQuote.toDomain(symbol: String): Quote = Quote(
        symbol = symbol,
        name = null,
        price = current ?: 0.0,
        previousClose = previousClose,
        change = change,
        percentChange = percentChange,
        open = open,
        high = high,
        low = low,
        volume = null,
        marketIsOpen = null,
        currency = "USD",
        fetchedAtMillis = System.currentTimeMillis()
    )

    private fun YahooChartResult.toQuote(symbol: String): Quote {
        val m = meta
        val price = m?.regularMarketPrice ?: apiError("Yahoo returned no price for $symbol")
        val prev = m.previousClose ?: m.chartPreviousClose
        val change = prev?.let { price - it }
        val percent = if (prev != null && prev != 0.0) (price - prev) / prev * 100.0 else null
        return Quote(
            symbol = m.symbol ?: symbol,
            name = m.longName ?: m.shortName,
            price = price,
            previousClose = prev,
            change = change,
            percentChange = percent,
            open = null,
            high = m.regularMarketDayHigh,
            low = m.regularMarketDayLow,
            volume = m.regularMarketVolume,
            marketIsOpen = null,
            currency = m.currency,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    private fun PriceCacheEntity.toDomain(): Quote = Quote(
        symbol = symbol,
        name = name,
        price = price,
        previousClose = previousClose,
        change = change,
        percentChange = percentChange,
        open = open,
        high = high,
        low = low,
        volume = volume,
        marketIsOpen = marketIsOpen,
        currency = currency,
        fetchedAtMillis = fetchedAtMillis
    )

    private fun Quote.toEntity(): PriceCacheEntity = PriceCacheEntity(
        symbol = symbol,
        price = price,
        previousClose = previousClose,
        change = change,
        percentChange = percentChange,
        open = open,
        high = high,
        low = low,
        volume = volume,
        marketIsOpen = marketIsOpen,
        currency = currency,
        name = name,
        fetchedAtMillis = fetchedAtMillis
    )

    private inline fun <T> runCatchingApi(block: () -> T): DataResult<T> = try {
        DataResult.Success(block())
    } catch (e: ApiException) {
        DataResult.Error(e.message ?: "Error", e.retryable)
    } catch (e: java.io.IOException) {
        DataResult.Error("Network error. Check your connection.", retryable = true)
    } catch (e: retrofit2.HttpException) {
        when (e.code()) {
            401, 403 -> DataResult.Error("Unauthorized. Check your API key in Settings.", retryable = false)
            429 -> DataResult.Error("Rate limit reached. Try again shortly.", retryable = true)
            else -> DataResult.Error("Server error (${e.code()}).", retryable = true)
        }
    } catch (e: Throwable) {
        DataResult.Error(e.message ?: "Unknown error.", retryable = true)
    }

    private fun apiError(msg: String): Nothing = throw ApiException(msg, retryable = true)

    private class ApiException(msg: String, val retryable: Boolean) : RuntimeException(msg)
}
