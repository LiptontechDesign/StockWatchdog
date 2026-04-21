package com.stockwatchdog.app.data.api.models

import kotlinx.serialization.Serializable

/**
 * Response shapes for Yahoo Finance's undocumented public JSON endpoints:
 *  - query1.finance.yahoo.com/v8/finance/chart/{symbol}
 *  - query2.finance.yahoo.com/v1/finance/search
 *
 * These are the same endpoints the `yfinance` Python library wraps, used
 * here as a no-API-key fallback so the app keeps working when keyed
 * providers are rate-limited.
 */

@Serializable
data class YahooChartEnvelope(
    val chart: YahooChart? = null
)

@Serializable
data class YahooChart(
    val result: List<YahooChartResult> = emptyList(),
    val error: YahooError? = null
)

@Serializable
data class YahooError(
    val code: String? = null,
    val description: String? = null
)

@Serializable
data class YahooChartResult(
    val meta: YahooChartMeta? = null,
    val timestamp: List<Long> = emptyList(),
    val indicators: YahooIndicators? = null
)

@Serializable
data class YahooChartMeta(
    val currency: String? = null,
    val symbol: String? = null,
    val exchangeName: String? = null,
    val instrumentType: String? = null,
    val regularMarketPrice: Double? = null,
    val chartPreviousClose: Double? = null,
    val previousClose: Double? = null,
    val regularMarketDayHigh: Double? = null,
    val regularMarketDayLow: Double? = null,
    val regularMarketVolume: Long? = null,
    val longName: String? = null,
    val shortName: String? = null
)

@Serializable
data class YahooIndicators(
    val quote: List<YahooQuoteArrays> = emptyList()
)

@Serializable
data class YahooQuoteArrays(
    val open: List<Double?> = emptyList(),
    val high: List<Double?> = emptyList(),
    val low: List<Double?> = emptyList(),
    val close: List<Double?> = emptyList(),
    val volume: List<Long?> = emptyList()
)

@Serializable
data class YahooSearchResponse(
    val quotes: List<YahooSearchQuote> = emptyList()
)

@Serializable
data class YahooSearchQuote(
    val symbol: String? = null,
    val shortname: String? = null,
    val longname: String? = null,
    val exchange: String? = null,
    val quoteType: String? = null,
    val typeDisp: String? = null
)
