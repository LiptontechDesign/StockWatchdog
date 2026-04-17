package com.stockwatchdog.app.domain

/**
 * Pure data class describing the derived P&L view of a held position relative
 * to its manual entry price. All fields are nullable so callers can degrade
 * gracefully when the user has only set some of the inputs.
 */
data class PositionPnl(
    /** Absolute $ change per share vs entry. */
    val perSharePnl: Double?,
    /** % change per share vs entry. */
    val percentPnl: Double?,
    /** Total $ P&L across the full position size (requires quantity). */
    val totalPnl: Double?,
    /** Current market value of the full position (requires quantity). */
    val positionValue: Double?,
    /** Original cost basis (entryPrice * quantity). */
    val costBasis: Double?
)

object PositionCalculator {
    fun calculate(
        currentPrice: Double?,
        entryPrice: Double?,
        quantity: Double?
    ): PositionPnl {
        if (currentPrice == null || entryPrice == null || entryPrice <= 0.0) {
            return PositionPnl(null, null, null, null, null)
        }
        val perShare = currentPrice - entryPrice
        val pct = (perShare / entryPrice) * 100.0
        val qty = quantity
        val costBasis = qty?.let { it * entryPrice }
        val positionValue = qty?.let { it * currentPrice }
        val total = qty?.let { perShare * it }
        return PositionPnl(
            perSharePnl = perShare,
            percentPnl = pct,
            totalPnl = total,
            positionValue = positionValue,
            costBasis = costBasis
        )
    }
}
