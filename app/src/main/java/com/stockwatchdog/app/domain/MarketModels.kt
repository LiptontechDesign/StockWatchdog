package com.stockwatchdog.app.domain

/** Normalized snapshot of current/last-available quote data. */
data class Quote(
    val symbol: String,
    val name: String?,
    val price: Double,
    val previousClose: Double?,
    val change: Double?,
    val percentChange: Double?,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val volume: Long?,
    val marketIsOpen: Boolean?,
    val currency: String?,
    val fetchedAtMillis: Long
)

data class PricePoint(
    val timestampMillis: Long,
    val close: Double
)

enum class ChartRange(val label: String) {
    ONE_DAY("1D"),
    FIVE_DAYS("5D"),
    ONE_MONTH("1M"),
    THREE_MONTHS("3M")
}

data class SymbolMatch(
    val symbol: String,
    val name: String?,
    val exchange: String?,
    val type: String?
)

sealed class DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>()
    data class Error(val message: String, val retryable: Boolean = true) : DataResult<Nothing>()
}
