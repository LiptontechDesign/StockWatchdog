package com.stockwatchdog.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertType {
    PRICE_ABOVE,
    PRICE_BELOW,
    PERCENT_CHANGE_DAY,
    /** Fires once when price rises to >= threshold% above your entry price. */
    PERCENT_ABOVE_ENTRY,
    /** Fires once when price falls to >= threshold% below your entry price. */
    PERCENT_BELOW_ENTRY,

    /**
     * Fires once per day when the next earnings event is within `threshold`
     * days. Defaults to 3 days. Uses StockDetails.nextEarningsEpochSeconds.
     */
    EARNINGS_REMINDER,

    /** Fires once when current price >= cached 52-week high. Re-arms on a new high. */
    FIFTY_TWO_WEEK_HIGH,

    /** Fires once when current price <= cached 52-week low. Re-arms on a new low. */
    FIFTY_TWO_WEEK_LOW,

    /** Fires when price closes above the 200-day moving average (golden-cross style). */
    MA200_CROSS_UP,

    /** Fires when price closes below the 200-day moving average (death-cross style). */
    MA200_CROSS_DOWN,

    /**
     * Fires when current/avg-volume ratio >= threshold (e.g. 2.0 = 2x avg).
     * De-duped to one fire per day.
     */
    VOLUME_SPIKE,

    /** Fires when price reaches the analyst mean target from StockDetails. */
    ANALYST_TARGET_REACH
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
    /**
     * Meaning depends on [type]:
     *  - PRICE_ABOVE / PRICE_BELOW       → absolute price
     *  - PERCENT_CHANGE_DAY              → percent magnitude
     *  - PERCENT_ABOVE_ENTRY / BELOW     → percent vs entry
     *  - EARNINGS_REMINDER               → days before earnings (1, 3, 7, …)
     *  - VOLUME_SPIKE                    → ratio (e.g. 2.0 = 2× avg volume)
     *  - FIFTY_TWO_WEEK_*, MA200_*,
     *    ANALYST_TARGET_REACH            → unused (set to 0)
     */
    val threshold: Double,
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastTriggeredAtMillis: Long? = null,
    /** Last observed side of the threshold; null means unknown/initial. */
    val lastCrossingState: Boolean? = null,
    /** ISO date (yyyy-MM-dd) of the last percent-change trigger; null if never. */
    val lastPercentTriggerDate: String? = null,
    /**
     * If true, the alert is automatically disabled after firing once.
     * Useful for take-profit / stop-loss style targets you only want to be
     * notified about a single time.
     */
    val autoDisableAfterFire: Boolean = false,
    /**
     * Suppress firing while `now < snoozedUntilMillis`. The worker still
     * tracks the underlying state so a snoozed alert resumes correctly
     * once the snooze window expires.
     */
    val snoozedUntilMillis: Long? = null,
    /** Optional note that appears in the notification body. */
    val notes: String? = null,
    /**
     * Per-alert override: only fire while NYSE is open. `null` falls back
     * to the global UserSettings.marketHoursOnly setting.
     */
    val marketHoursOnly: Boolean? = null,
    /** Last observed price seen by the worker; used by the UI for distance-to-trigger. */
    val lastPrice: Double? = null
)
