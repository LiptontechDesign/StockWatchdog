package com.stockwatchdog.app.domain

import com.stockwatchdog.app.data.db.entities.PositionLotEntity

/**
 * Aggregate P&L view for a position made up of one or more lots.
 *
 * - [totalInvested] = sum of amount invested across all lots
 * - [positionValue] = current market value (lot quantities × current price)
 * - [totalPnl]      = positionValue − totalInvested
 * - [percentPnl]    = totalPnl / totalInvested × 100
 * - [avgEntryPrice] = totalInvested / totalQuantity (weighted). Kept for
 *   internal use (e.g. "% vs entry" alert evaluation). Not shown in UI.
 * - [perLot]        = P&L breakdown per individual lot so each "Position N"
 *   card can display its own +$/% since entry.
 *
 * All fields are nullable so callers can render "—" when there isn't enough
 * information (e.g. no lots yet, or no current price available).
 */
data class PositionPnl(
    val totalInvested: Double?,
    val positionValue: Double?,
    val totalPnl: Double?,
    val percentPnl: Double?,
    val avgEntryPrice: Double?,
    val totalQuantity: Double?,
    val perLot: List<LotPnl> = emptyList()
)

/** P&L for a single lot. */
data class LotPnl(
    val lotId: Long,
    val entryPrice: Double,
    val amountInvested: Double,
    /** quantity = amountInvested / entryPrice. */
    val quantity: Double,
    /** current $ gain/loss vs original amount invested. Null if no price. */
    val pnl: Double?,
    /** current % gain/loss vs entry price. Null if no price. */
    val percentPnl: Double?,
    /** current market value (quantity × currentPrice). Null if no price. */
    val value: Double?
)

object PositionCalculator {

    /** Aggregate a list of lots into a single [PositionPnl]. */
    fun calculate(
        currentPrice: Double?,
        lots: List<PositionLotEntity>
    ): PositionPnl {
        if (lots.isEmpty()) {
            return PositionPnl(null, null, null, null, null, null)
        }
        val totalInvested = lots.sumOf { it.amountInvested }
        val totalQuantity = lots.sumOf { safeQuantity(it) }
        val avgEntry = if (totalQuantity > 0) totalInvested / totalQuantity else null
        val value = currentPrice?.let { price -> totalQuantity * price }
        val pnl = value?.let { it - totalInvested }
        val pct = if (totalInvested > 0.0 && pnl != null) pnl / totalInvested * 100.0 else null

        val perLot = lots.map { lot ->
            val q = safeQuantity(lot)
            val lotValue = currentPrice?.let { q * it }
            val lotPnl = lotValue?.let { it - lot.amountInvested }
            val lotPct = if (lot.amountInvested > 0 && lotPnl != null)
                lotPnl / lot.amountInvested * 100.0
            else null
            LotPnl(
                lotId = lot.id,
                entryPrice = lot.entryPrice,
                amountInvested = lot.amountInvested,
                quantity = q,
                pnl = lotPnl,
                percentPnl = lotPct,
                value = lotValue
            )
        }

        return PositionPnl(
            totalInvested = totalInvested,
            positionValue = value,
            totalPnl = pnl,
            percentPnl = pct,
            avgEntryPrice = avgEntry,
            totalQuantity = totalQuantity,
            perLot = perLot
        )
    }

    /** Backwards-compatible helper for legacy callers that still use a single
     *  (entryPrice, quantity) pair. Internally builds a synthetic one-lot
     *  list and reuses the aggregate calculation. */
    fun calculate(
        currentPrice: Double?,
        entryPrice: Double?,
        quantity: Double?
    ): PositionPnl {
        if (entryPrice == null || entryPrice <= 0.0) {
            return PositionPnl(null, null, null, null, null, null)
        }
        val q = quantity ?: 1.0
        val synthetic = listOf(
            PositionLotEntity(
                id = 0,
                symbol = "",
                entryPrice = entryPrice,
                amountInvested = entryPrice * q,
                addedAtMillis = 0L
            )
        )
        return calculate(currentPrice, synthetic)
    }

    private fun safeQuantity(lot: PositionLotEntity): Double =
        if (lot.entryPrice > 0) lot.amountInvested / lot.entryPrice else 0.0
}
