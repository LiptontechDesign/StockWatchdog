package com.stockwatchdog.app.domain

import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Result of evaluating an alert against a fresh [Quote].
 *
 * [shouldNotify] indicates that the alert has just transitioned and a
 * notification should fire. [updated] always reflects the new state that
 * should be persisted back to the database (to remember crossing state and
 * date-based trigger guards so we don't re-notify on every tick).
 */
data class AlertEvaluation(
    val original: AlertEntity,
    val updated: AlertEntity,
    val shouldNotify: Boolean,
    val message: String? = null
)

object AlertEvaluator {

    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * Evaluate `alert` against the latest market data.
     *
     * @param details Optional Yahoo `quoteSummary` snapshot. Required for
     *  earnings, 52-week, MA200, volume-spike and analyst-target alerts.
     *  When `null`, those alert types stay dormant (we never spam-fire on
     *  missing data).
     */
    fun evaluate(
        alert: AlertEntity,
        quote: Quote,
        now: Long = System.currentTimeMillis(),
        entryPrice: Double? = null,
        platformFeePercent: Double = 0.0,
        details: StockDetails? = null
    ): AlertEvaluation {
        if (!alert.enabled) return AlertEvaluation(alert, alert, shouldNotify = false)

        return when (alert.type) {
            AlertType.PRICE_ABOVE -> evalAbove(alert, quote, now)
            AlertType.PRICE_BELOW -> evalBelow(alert, quote, now)
            AlertType.PERCENT_CHANGE_DAY -> evalPercent(alert, quote, now)
            AlertType.PERCENT_ABOVE_ENTRY -> evalAboveEntry(alert, quote, entryPrice, platformFeePercent, now)
            AlertType.PERCENT_BELOW_ENTRY -> evalBelowEntry(alert, quote, entryPrice, platformFeePercent, now)
            AlertType.EARNINGS_REMINDER -> evalEarnings(alert, quote, details, now)
            AlertType.FIFTY_TWO_WEEK_HIGH -> eval52wHigh(alert, quote, details, now)
            AlertType.FIFTY_TWO_WEEK_LOW -> eval52wLow(alert, quote, details, now)
            AlertType.MA200_CROSS_UP -> evalMa200Up(alert, quote, details, now)
            AlertType.MA200_CROSS_DOWN -> evalMa200Down(alert, quote, details, now)
            AlertType.VOLUME_SPIKE -> evalVolumeSpike(alert, quote, details, now)
            AlertType.ANALYST_TARGET_REACH -> evalAnalystTarget(alert, quote, details, now)
        }
    }

    // ---- Price-based ----------------------------------------------------

    private fun evalAbove(a: AlertEntity, q: Quote, now: Long): AlertEvaluation {
        val isAbove = q.price > a.threshold
        val previouslyAbove = a.lastCrossingState ?: false
        val shouldNotify = isAbove && !previouslyAbove
        val message = if (shouldNotify)
            "${a.symbol} crossed above ${formatThreshold(a.threshold)}"
        else null
        return persist(a, isAbove, shouldNotify, message, q.price, now)
    }

    private fun evalBelow(a: AlertEntity, q: Quote, now: Long): AlertEvaluation {
        val isBelow = q.price < a.threshold
        val previouslyBelow = a.lastCrossingState ?: false
        val shouldNotify = isBelow && !previouslyBelow
        val message = if (shouldNotify)
            "${a.symbol} dropped below ${formatThreshold(a.threshold)}"
        else null
        return persist(a, isBelow, shouldNotify, message, q.price, now)
    }

    private fun evalPercent(a: AlertEntity, q: Quote, now: Long): AlertEvaluation {
        val pct = q.percentChange ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val today = isoDate.format(Date(now))
        val alreadyFiredToday = a.lastPercentTriggerDate == today
        val triggered = abs(pct) >= abs(a.threshold)
        val shouldNotify = triggered && !alreadyFiredToday
        val message = if (shouldNotify) {
            val dir = if (pct >= 0) "up" else "down"
            "${a.symbol} moved $dir ${"%.2f".format(pct)}% today (rule: ${"%.2f".format(a.threshold)}%)"
        } else null
        val updated = a.copy(
            lastPercentTriggerDate = if (shouldNotify) today else a.lastPercentTriggerDate,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis,
            lastPrice = q.price
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    // ---- Entry-relative -------------------------------------------------

    private fun evalAboveEntry(
        a: AlertEntity,
        q: Quote,
        entryPrice: Double?,
        platformFeePercent: Double,
        now: Long
    ): AlertEvaluation {
        if (entryPrice == null || entryPrice <= 0.0) {
            return AlertEvaluation(a, a, shouldNotify = false)
        }
        val pct = PositionCalculator.netPercentVsEntry(q.price, entryPrice, platformFeePercent)
        val triggered = pct >= a.threshold
        val previously = a.lastCrossingState ?: false
        val shouldNotify = triggered && !previously
        val message = if (shouldNotify)
            "${a.symbol} is ${"%.2f".format(pct)}% net vs your entry " +
                "(target: +${"%.2f".format(a.threshold)}%)"
        else null
        return persist(a, triggered, shouldNotify, message, q.price, now)
    }

    private fun evalBelowEntry(
        a: AlertEntity,
        q: Quote,
        entryPrice: Double?,
        platformFeePercent: Double,
        now: Long
    ): AlertEvaluation {
        if (entryPrice == null || entryPrice <= 0.0) {
            return AlertEvaluation(a, a, shouldNotify = false)
        }
        val pct = PositionCalculator.netPercentVsEntry(q.price, entryPrice, platformFeePercent)
        val triggered = pct <= -a.threshold
        val previously = a.lastCrossingState ?: false
        val shouldNotify = triggered && !previously
        val message = if (shouldNotify)
            "${a.symbol} is ${"%.2f".format(pct)}% net vs your entry " +
                "(trigger: -${"%.2f".format(a.threshold)}%)"
        else null
        return persist(a, triggered, shouldNotify, message, q.price, now)
    }

    // ---- Details-driven (Yahoo quoteSummary) ----------------------------

    private fun evalEarnings(
        a: AlertEntity,
        q: Quote,
        details: StockDetails?,
        now: Long
    ): AlertEvaluation {
        val eps = details?.nextEarningsEpochSeconds
            ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val daysAway = (eps * 1000L - now).toDouble() / 86_400_000.0
        val daysWanted = if (a.threshold > 0) a.threshold else 3.0
        val withinWindow = daysAway in 0.0..daysWanted
        val today = isoDate.format(Date(now))
        val alreadyFiredToday = a.lastPercentTriggerDate == today
        val shouldNotify = withinWindow && !alreadyFiredToday
        val message = if (shouldNotify) {
            val rounded = kotlin.math.max(0L, daysAway.toLong())
            val whenStr = com.stockwatchdog.app.util.MarketClock.formatKenyaLong(eps)
            if (rounded == 0L) "${a.symbol} reports earnings TODAY \u00b7 $whenStr"
            else "${a.symbol} reports earnings in $rounded day${if (rounded == 1L) "" else "s"} \u00b7 $whenStr"
        } else null
        val updated = a.copy(
            lastPercentTriggerDate = if (shouldNotify) today else a.lastPercentTriggerDate,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis,
            lastPrice = q.price
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun eval52wHigh(
        a: AlertEntity,
        q: Quote,
        details: StockDetails?,
        now: Long
    ): AlertEvaluation {
        val hi = details?.fiftyTwoWeekHigh
            ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val isAt = q.price >= hi
        val previously = a.lastCrossingState ?: false
        val shouldNotify = isAt && !previously
        val message = if (shouldNotify)
            "${a.symbol} touched a 52-week high (${formatThreshold(q.price)})"
        else null
        return persist(a, isAt, shouldNotify, message, q.price, now)
    }

    private fun eval52wLow(
        a: AlertEntity,
        q: Quote,
        details: StockDetails?,
        now: Long
    ): AlertEvaluation {
        val lo = details?.fiftyTwoWeekLow
            ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val isAt = q.price <= lo
        val previously = a.lastCrossingState ?: false
        val shouldNotify = isAt && !previously
        val message = if (shouldNotify)
            "${a.symbol} touched a 52-week low (${formatThreshold(q.price)})"
        else null
        return persist(a, isAt, shouldNotify, message, q.price, now)
    }

    private fun evalMa200Up(
        a: AlertEntity,
        q: Quote,
        details: StockDetails?,
        now: Long
    ): AlertEvaluation {
        val ma = details?.twoHundredDayAverage
            ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val above = q.price >= ma
        val previously = a.lastCrossingState ?: false
        val shouldNotify = above && !previously
        val message = if (shouldNotify)
            "${a.symbol} crossed above its 200-day MA (${formatThreshold(ma)})"
        else null
        return persist(a, above, shouldNotify, message, q.price, now)
    }

    private fun evalMa200Down(
        a: AlertEntity,
        q: Quote,
        details: StockDetails?,
        now: Long
    ): AlertEvaluation {
        val ma = details?.twoHundredDayAverage
            ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val below = q.price <= ma
        val previously = a.lastCrossingState ?: false
        val shouldNotify = below && !previously
        val message = if (shouldNotify)
            "${a.symbol} crossed below its 200-day MA (${formatThreshold(ma)})"
        else null
        return persist(a, below, shouldNotify, message, q.price, now)
    }

    private fun evalVolumeSpike(
        a: AlertEntity,
        q: Quote,
        details: StockDetails?,
        now: Long
    ): AlertEvaluation {
        val ratio = details?.volumeSpikeRatio()
            ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val want = if (a.threshold > 0) a.threshold else 2.0
        val today = isoDate.format(Date(now))
        val alreadyFiredToday = a.lastPercentTriggerDate == today
        val triggered = ratio >= want
        val shouldNotify = triggered && !alreadyFiredToday
        val message = if (shouldNotify)
            "${a.symbol} unusual volume: %.1f\u00d7 average".format(ratio)
        else null
        val updated = a.copy(
            lastPercentTriggerDate = if (shouldNotify) today else a.lastPercentTriggerDate,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis,
            lastPrice = q.price
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun evalAnalystTarget(
        a: AlertEntity,
        q: Quote,
        details: StockDetails?,
        now: Long
    ): AlertEvaluation {
        val target = details?.analystTargetMean
            ?: return AlertEvaluation(a, a.copy(lastPrice = q.price), shouldNotify = false)
        val reached = q.price >= target
        val previously = a.lastCrossingState ?: false
        val shouldNotify = reached && !previously
        val message = if (shouldNotify)
            "${a.symbol} reached analyst target (${formatThreshold(target)})"
        else null
        return persist(a, reached, shouldNotify, message, q.price, now)
    }

    // ---- Helpers --------------------------------------------------------

    private fun persist(
        a: AlertEntity,
        crossing: Boolean,
        shouldNotify: Boolean,
        message: String?,
        price: Double,
        now: Long
    ): AlertEvaluation {
        val updated = a.copy(
            lastCrossingState = crossing,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis,
            lastPrice = price
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun formatThreshold(v: Double): String =
        if (v % 1.0 == 0.0) "%.2f".format(v) else "%.2f".format(v)
}
