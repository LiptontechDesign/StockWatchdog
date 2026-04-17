package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertType {
    PRICE_ABOVE,
    PRICE_BELOW,
    PERCENT_CHANGE_DAY
}

/**
 * The [lastCrossingState] field is used to prevent notification spam:
 *  - For PRICE_ABOVE: false when price was below threshold, true when above.
 *    We only fire when it flips false -> true.
 *  - For PRICE_BELOW: inverse.
 *  - For PERCENT_CHANGE_DAY: fires once per day once |change%| >= threshold.
 *    Resets when the date rolls over.
 */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val type: AlertType,
    /** Threshold price (for PRICE_ABOVE/PRICE_BELOW) or percent (for PERCENT_CHANGE_DAY). */
    val threshold: Double,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastTriggeredAtMillis: Long? = null,
    /** Last observed side of the threshold; null means unknown/initial. */
    val lastCrossingState: Boolean? = null,
    /** ISO date (yyyy-MM-dd) of the last percent-change trigger; null if never. */
    val lastPercentTriggerDate: String? = null
)
