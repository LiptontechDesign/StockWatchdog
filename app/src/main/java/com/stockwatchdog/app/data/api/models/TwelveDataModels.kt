package com.stockwatchdog.app.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TwelveQuote(
    val symbol: String? = null,
    val name: String? = null,
    val exchange: String? = null,
    val currency: String? = null,
    val datetime: String? = null,
    val open: String? = null,
    val high: String? = null,
    val low: String? = null,
    val close: String? = null,
    val volume: String? = null,
    val previous_close: String? = null,
    val change: String? = null,
    val percent_change: String? = null,
    val is_market_open: Boolean? = null,
    /** Error responses reuse this field. */
    val status: String? = null,
    val code: Int? = null,
    val message: String? = null
)

@Serializable
data class TwelvePrice(
    val price: String? = null,
    val status: String? = null,
    val code: Int? = null,
    val message: String? = null
)

@Serializable
data class TwelveTimeSeries(
    val meta: TwelveMeta? = null,
    val values: List<TwelveCandle> = emptyList(),
    val status: String? = null,
    val code: Int? = null,
    val message: String? = null
)

@Serializable
data class TwelveMeta(
    val symbol: String? = null,
    val interval: String? = null,
    val currency: String? = null,
    val exchange_timezone: String? = null,
    val exchange: String? = null,
    val type: String? = null
)

@Serializable
data class TwelveCandle(
    val datetime: String,
    val open: String? = null,
    val high: String? = null,
    val low: String? = null,
    val close: String? = null,
    val volume: String? = null
)

@Serializable
data class TwelveSymbolSearch(
    val data: List<TwelveSymbolMatch> = emptyList()
)

@Serializable
data class TwelveSymbolMatch(
    val symbol: String,
    val instrument_name: String? = null,
    val exchange: String? = null,
    val country: String? = null,
    @SerialName("instrument_type") val instrumentType: String? = null
)
