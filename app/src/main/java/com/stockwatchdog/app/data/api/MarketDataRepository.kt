package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.AlphaCandle
import com.stockwatchdog.app.data.api.models.TwelveQuote
import com.stockwatchdog.app.data.db.PriceCacheDao
import com.stockwatchdog.app.data.db.entities.PriceCacheEntity
import com.stockwatchdog.app.data.prefs.ApiProvider
import com.stockwatchdog.app.data.prefs.SettingsRepository
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
 * Single entry point for market data. Selects the active API provider from
 * [SettingsRepository], normalizes results to [Quote]/[PricePoint], caches
 * quotes in Room for offline viewing, and maps errors to friendly messages.
 */
class MarketDataRepository(
    private val twelveData: TwelveDataApi,
    private val alphaVantage: AlphaVantageApi,
    private val settings: SettingsRepository,
    private val priceCacheDao: PriceCacheDao
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
        // Sequential to respect free-tier rate limits (Twelve Data free ~8 req/min).
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
        runCatchingApi {
            when (s.provider) {
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
                    val r = alphaVantage.search(query, s.activeKey)
                    r.note?.let { return@runCatchingApi error(it) }
                    r.information?.let { return@runCatchingApi error(it) }
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
            }
        }
    }

    suspend fun getSeries(symbol: String, range: ChartRange): DataResult<List<PricePoint>> =
        withContext(Dispatchers.IO) {
            val s = settings.settings.first()
            runCatchingApi {
                when (s.provider) {
                    ApiProvider.TWELVE_DATA -> {
                        val (interval, outputSize) = when (range) {
                            ChartRange.ONE_DAY -> "5min" to 78
                            ChartRange.FIVE_DAYS -> "30min" to 65
                            ChartRange.ONE_MONTH -> "1day" to 30
                            ChartRange.THREE_MONTHS -> "1day" to 90
                        }
                        val r = twelveData.timeSeries(symbol, interval, outputSize, s.activeKey)
                        if (r.status == "error") return@runCatchingApi error(r.message ?: "API error")
                        val fmt = if (interval == "1day")
                            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        else
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        fmt.timeZone = TimeZone.getTimeZone("UTC")
                        // Twelve Data returns newest-first; reverse to oldest-first.
                        r.values.asReversed().mapNotNull { c ->
                            val close = c.close?.toDoubleOrNull() ?: return@mapNotNull null
                            val t = runCatching { fmt.parse(c.datetime)?.time }.getOrNull()
                                ?: return@mapNotNull null
                            PricePoint(t, close)
                        }
                    }
                    ApiProvider.ALPHA_VANTAGE -> {
                        when (range) {
                            ChartRange.ONE_DAY, ChartRange.FIVE_DAYS -> {
                                val interval = if (range == ChartRange.ONE_DAY) "5min" else "30min"
                                val r = alphaVantage.intraday(symbol, interval, "compact", s.activeKey)
                                r.note?.let { return@runCatchingApi error(it) }
                                r.information?.let { return@runCatchingApi error(it) }
                                r.errorMessage?.let { return@runCatchingApi error(it) }
                                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                fmt.timeZone = TimeZone.getTimeZone("UTC")
                                seriesToPoints(r.series(), fmt)
                            }
                            ChartRange.ONE_MONTH, ChartRange.THREE_MONTHS -> {
                                val r = alphaVantage.daily(symbol, "compact", s.activeKey)
                                r.note?.let { return@runCatchingApi error(it) }
                                r.information?.let { return@runCatchingApi error(it) }
                                r.errorMessage?.let { return@runCatchingApi error(it) }
                                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                fmt.timeZone = TimeZone.getTimeZone("UTC")
                                val points = seriesToPoints(r.series, fmt)
                                val take = if (range == ChartRange.ONE_MONTH) 22 else 66
                                points.takeLast(take)
                            }
                        }
                    }
                }
            }
        }

    private fun seriesToPoints(
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

    private suspend fun fetchQuote(symbol: String): DataResult<Quote> = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        if (s.activeKey.isBlank()) {
            return@withContext DataResult.Error(
                "No API key set for ${s.provider.name}. Add one in Settings.",
                retryable = false
            )
        }
        runCatchingApi {
            when (s.provider) {
                ApiProvider.TWELVE_DATA -> {
                    val q = twelveData.quote(symbol, s.activeKey)
                    if (q.status == "error") return@runCatchingApi error(q.message ?: "API error")
                    q.toDomain() ?: return@runCatchingApi error("Unexpected empty response")
                }
                ApiProvider.ALPHA_VANTAGE -> {
                    val env = alphaVantage.globalQuote(symbol, s.activeKey)
                    env.note?.let { return@runCatchingApi error(it) }
                    env.information?.let { return@runCatchingApi error(it) }
                    env.errorMessage?.let { return@runCatchingApi error(it) }
                    val q = env.quote
                        ?: return@runCatchingApi error("No quote returned for $symbol")
                    val price = q.price?.toDoubleOrNull()
                        ?: return@runCatchingApi error("No price returned for $symbol")
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
            }
        }
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

    private fun error(msg: String): Nothing = throw ApiException(msg, retryable = true)

    private class ApiException(msg: String, val retryable: Boolean) : RuntimeException(msg)
}
