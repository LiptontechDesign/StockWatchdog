package com.stockwatchdog.app.domain

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

    fun evaluate(
        alert: AlertEntity,
        quote: Quote,
        now: Long = System.currentTimeMillis(),
        entryPrice: Double? = null
    ): AlertEvaluation {
        if (!alert.enabled) return AlertEvaluation(alert, alert, shouldNotify = false)

        return when (alert.type) {
            AlertType.PRICE_ABOVE -> evalAbove(alert, quote, now)
            AlertType.PRICE_BELOW -> evalBelow(alert, quote, now)
            AlertType.PERCENT_CHANGE_DAY -> evalPercent(alert, quote, now)
            AlertType.PERCENT_ABOVE_ENTRY -> evalAboveEntry(alert, quote, entryPrice, now)
            AlertType.PERCENT_BELOW_ENTRY -> evalBelowEntry(alert, quote, entryPrice, now)
        }
    }

    private fun evalAbove(a: AlertEntity, q: Quote, now: Long): AlertEvaluation {
        val isAbove = q.price > a.threshold
        val previouslyAbove = a.lastCrossingState ?: false
        // Fire once when crossing up. Re-arms automatically after price drops back below.
        val shouldNotify = isAbove && !previouslyAbove
        val message = if (shouldNotify)
            "${a.symbol} crossed above ${formatThreshold(a.threshold)}"
        else null
        val updated = a.copy(
            lastCrossingState = isAbove,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun evalBelow(a: AlertEntity, q: Quote, now: Long): AlertEvaluation {
        val isBelow = q.price < a.threshold
        val previouslyBelow = a.lastCrossingState ?: false
        val shouldNotify = isBelow && !previouslyBelow
        val message = if (shouldNotify)
            "${a.symbol} dropped below ${formatThreshold(a.threshold)}"
        else null
        val updated = a.copy(
            lastCrossingState = isBelow,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun evalPercent(a: AlertEntity, q: Quote, now: Long): AlertEvaluation {
        val pct = q.percentChange ?: return AlertEvaluation(a, a, shouldNotify = false)
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
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun evalAboveEntry(
        a: AlertEntity,
        q: Quote,
        entryPrice: Double?,
        now: Long
    ): AlertEvaluation {
        if (entryPrice == null || entryPrice <= 0.0) {
            // No entry price tracked yet; keep the alert dormant without updating state.
            return AlertEvaluation(a, a, shouldNotify = false)
        }
        val pct = (q.price - entryPrice) / entryPrice * 100.0
        val triggered = pct >= a.threshold
        val previously = a.lastCrossingState ?: false
        val shouldNotify = triggered && !previously
        val message = if (shouldNotify)
            "${a.symbol} is ${"%.2f".format(pct)}% above your entry " +
                "(target: +${"%.2f".format(a.threshold)}%)"
        else null
        val updated = a.copy(
            lastCrossingState = triggered,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun evalBelowEntry(
        a: AlertEntity,
        q: Quote,
        entryPrice: Double?,
        now: Long
    ): AlertEvaluation {
        if (entryPrice == null || entryPrice <= 0.0) {
            return AlertEvaluation(a, a, shouldNotify = false)
        }
        val pct = (q.price - entryPrice) / entryPrice * 100.0
        // User enters a positive magnitude: e.g. 5 means "fire when down 5% or more".
        val triggered = pct <= -a.threshold
        val previously = a.lastCrossingState ?: false
        val shouldNotify = triggered && !previously
        val message = if (shouldNotify)
            "${a.symbol} is ${"%.2f".format(pct)}% vs your entry " +
                "(trigger: -${"%.2f".format(a.threshold)}%)"
        else null
        val updated = a.copy(
            lastCrossingState = triggered,
            lastTriggeredAtMillis = if (shouldNotify) now else a.lastTriggeredAtMillis
        )
        return AlertEvaluation(a, updated, shouldNotify, message)
    }

    private fun formatThreshold(v: Double): String =
        if (v % 1.0 == 0.0) "%.2f".format(v) else "%.2f".format(v)
}
