package com.stockwatchdog.app.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlphaGlobalQuoteEnvelope(
    @SerialName("Global Quote") val quote: AlphaGlobalQuote? = null,
    @SerialName("Note") val note: String? = null,
    @SerialName("Information") val information: String? = null,
    @SerialName("Error Message") val errorMessage: String? = null
)

@Serializable
data class AlphaGlobalQuote(
    @SerialName("01. symbol") val symbol: String? = null,
    @SerialName("02. open") val open: String? = null,
    @SerialName("03. high") val high: String? = null,
    @SerialName("04. low") val low: String? = null,
    @SerialName("05. price") val price: String? = null,
    @SerialName("06. volume") val volume: String? = null,
    @SerialName("07. latest trading day") val latestTradingDay: String? = null,
    @SerialName("08. previous close") val previousClose: String? = null,
    @SerialName("09. change") val change: String? = null,
    @SerialName("10. change percent") val changePercent: String? = null
)

@Serializable
data class AlphaSymbolSearch(
    @SerialName("bestMatches") val bestMatches: List<AlphaSymbolMatch> = emptyList(),
    @SerialName("Note") val note: String? = null,
    @SerialName("Information") val information: String? = null
)

@Serializable
data class AlphaSymbolMatch(
    @SerialName("1. symbol") val symbol: String? = null,
    @SerialName("2. name") val name: String? = null,
    @SerialName("3. type") val type: String? = null,
    @SerialName("4. region") val region: String? = null,
    @SerialName("8. currency") val currency: String? = null
)

@Serializable
data class AlphaTimeSeriesIntraday(
    @SerialName("Meta Data") val meta: Map<String, String>? = null,
    @SerialName("Time Series (5min)") val series5min: Map<String, AlphaCandle>? = null,
    @SerialName("Time Series (15min)") val series15min: Map<String, AlphaCandle>? = null,
    @SerialName("Time Series (30min)") val series30min: Map<String, AlphaCandle>? = null,
    @SerialName("Time Series (60min)") val series60min: Map<String, AlphaCandle>? = null,
    @SerialName("Note") val note: String? = null,
    @SerialName("Information") val information: String? = null,
    @SerialName("Error Message") val errorMessage: String? = null
) {
    fun series(): Map<String, AlphaCandle>? =
        series5min ?: series15min ?: series30min ?: series60min
}

@Serializable
data class AlphaTimeSeriesDaily(
    @SerialName("Meta Data") val meta: Map<String, String>? = null,
    @SerialName("Time Series (Daily)") val series: Map<String, AlphaCandle>? = null,
    @SerialName("Note") val note: String? = null,
    @SerialName("Information") val information: String? = null,
    @SerialName("Error Message") val errorMessage: String? = null
)

@Serializable
data class AlphaCandle(
    @SerialName("1. open") val open: String? = null,
    @SerialName("2. high") val high: String? = null,
    @SerialName("3. low") val low: String? = null,
    @SerialName("4. close") val close: String? = null,
    @SerialName("5. volume") val volume: String? = null
)

/**
 * Subset of the Alpha Vantage `OVERVIEW` endpoint response we actually
 * use for the Dip Finder. The endpoint returns ~60 fields; we only
 * deserialize the ones that drive the dip score so the JSON cost stays
 * tiny. Free-tier limits apply (~25 calls/day total across all symbols).
 *
 * `"Note"` and `"Information"` come back when the free-tier daily limit
 * has been exceeded — callers must surface them as a friendly error.
 */
@Serializable
data class AlphaCompanyOverview(
    @SerialName("Symbol") val symbol: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("AssetType") val assetType: String? = null,
    @SerialName("Sector") val sector: String? = null,
    @SerialName("Industry") val industry: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("EPS") val eps: String? = null,
    @SerialName("RevenueTTM") val revenueTtm: String? = null,
    @SerialName("PERatio") val peRatio: String? = null,
    @SerialName("PEGRatio") val pegRatio: String? = null,
    @SerialName("PriceToBookRatio") val priceToBookRatio: String? = null,
    @SerialName("ProfitMargin") val profitMargin: String? = null,
    @SerialName("OperatingMarginTTM") val operatingMargin: String? = null,
    @SerialName("QuarterlyRevenueGrowthYOY") val quarterlyRevenueGrowthYoY: String? = null,
    @SerialName("QuarterlyEarningsGrowthYOY") val quarterlyEarningsGrowthYoY: String? = null,
    @SerialName("DebtToEquity") val debtToEquity: String? = null,
    @SerialName("52WeekHigh") val high52w: String? = null,
    @SerialName("52WeekLow") val low52w: String? = null,
    @SerialName("200DayMovingAverage") val ma200: String? = null,
    @SerialName("Note") val note: String? = null,
    @SerialName("Information") val information: String? = null
)
