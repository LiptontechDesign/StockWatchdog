package com.stockwatchdog.app.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape for Finnhub `/quote` endpoint.
 * https://finnhub.io/docs/api/quote
 *
 *  c: current price, d: change, dp: percent change,
 *  h: day high, l: day low, o: open, pc: previous close, t: epoch seconds
 */
@Serializable
data class FinnhubQuote(
    @SerialName("c") val current: Double? = null,
    @SerialName("d") val change: Double? = null,
    @SerialName("dp") val percentChange: Double? = null,
    @SerialName("h") val high: Double? = null,
    @SerialName("l") val low: Double? = null,
    @SerialName("o") val open: Double? = null,
    @SerialName("pc") val previousClose: Double? = null,
    @SerialName("t") val timestamp: Long? = null
)

/**
 * Response shape for Finnhub `/search` endpoint.
 * https://finnhub.io/docs/api/symbol-search
 */
@Serializable
data class FinnhubSymbolSearch(
    val count: Int = 0,
    val result: List<FinnhubSymbolMatch> = emptyList()
)

@Serializable
data class FinnhubSymbolMatch(
    val symbol: String,
    val description: String? = null,
    val displaySymbol: String? = null,
    val type: String? = null
)

@Serializable
data class FinnhubEarningsCalendarResponse(
    val earningsCalendar: List<FinnhubEarningsCalendarEvent> = emptyList()
)

@Serializable
data class FinnhubEarningsCalendarEvent(
    val date: String? = null,
    val epsActual: Double? = null,
    val epsEstimate: Double? = null,
    val hour: String? = null,
    val quarter: Int? = null,
    val symbol: String? = null,
    val year: Int? = null
)

@Serializable
data class FinnhubRecommendationTrend(
    val symbol: String? = null,
    val period: String? = null,
    val strongBuy: Int? = null,
    val buy: Int? = null,
    val hold: Int? = null,
    val sell: Int? = null,
    val strongSell: Int? = null
)
