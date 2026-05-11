package com.stockwatchdog.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stockwatchdog.app.StockWatchdogApp
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.data.db.AlertEventDao
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertEventEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.domain.AlertEvaluator
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.notifications.NotificationHelper
import com.stockwatchdog.app.util.MarketClock
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Periodic worker that:
 *  1. Loads all enabled alerts.
 *  2. Fetches a fresh quote (always) and StockDetails (only when needed)
 *     for each distinct symbol.
 *  3. Evaluates each alert, fires notifications for new transitions,
 *     persists updated state, **logs every fire to alert_events**, and
 *     respects per-alert snooze, auto-disable, market-hours-only, plus
 *     the global quiet-hours window.
 *
 * Suppressed notifications (quiet hours / market closed) are still
 * recorded in history so the user sees them in the morning.
 */
class AlertCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? StockWatchdogApp ?: return Result.success()
        val container = app.container
        val settings = container.settingsRepository.settings.first()
        if (!settings.notificationsEnabled) return Result.success()

        val alertDao = container.database.alertDao()
        val alertEventDao = container.database.alertEventDao()
        val watchlistDao = container.database.watchlistDao()
        val positionLotDao = container.database.positionLotDao()
        val now = System.currentTimeMillis()

        val trackedSymbols = (
            watchlistDao.getAll().map { it.symbol } +
                container.database.dipTrackerDao().getAll().map { it.symbol }
            )
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()

        val enabled = alertDao.getAllEnabled()

        // Filter out snoozed alerts up front; their state is preserved.
        val active = enabled.filter { it.isActiveAt(now) }
        if (active.isEmpty() && trackedSymbols.isEmpty()) return Result.success()

        val symbols = active.map { it.symbol }.distinct()
        val quotes = if (symbols.isNotEmpty()) {
            container.marketDataRepository.refreshQuotes(symbols)
        } else emptyMap()

        // Fetch StockDetails only for alert/auto-earnings symbols that need them. Cached for 6h.
        val needDetailsSyms = (
            active.filter { it.type.requiresDetails() }.map { it.symbol } + trackedSymbols
        ).distinct()
        val details = if (needDetailsSyms.isNotEmpty()) {
            container.stockDetailsRepository.getMany(needDetailsSyms)
        } else emptyMap()

        val entryPrices: Map<String, Double?> = symbols.associateWith { sym ->
            val lots = positionLotDao.getBySymbol(sym)
            val totalInvested = lots.sumOf { it.amountInvested }
            val totalQty = lots.sumOf {
                if (it.entryPrice > 0) it.amountInvested / it.entryPrice else 0.0
            }
            if (totalQty > 0) totalInvested / totalQty
            else watchlistDao.getBySymbol(sym)?.entryPrice
        }

        val isQuietHours = settings.quietHoursEnabled &&
            MarketClock.isInQuietHoursKenya(
                nowUtcMillis = now,
                startMinutes = settings.quietHoursStartMinutes,
                endMinutes = settings.quietHoursEndMinutes
            )
        val isMarketOpen = MarketClock.status(MarketClock.Market.US_NYSE, now).isOpen

        var notifId = (now and 0x7fffffff).toInt()
        notifId = fireAutomaticEarningsAlerts(
            context = applicationContext,
            alertEventDao = alertEventDao,
            details = details.filterKeys { it in trackedSymbols },
            now = now,
            isQuietHours = isQuietHours,
            startingNotificationId = notifId
        )

        for (alert in active) {
            val res = quotes[alert.symbol] ?: continue
            if (res !is DataResult.Success) continue
            val quote = res.value

            val eval = AlertEvaluator.evaluate(
                alert = alert,
                quote = quote,
                entryPrice = entryPrices[alert.symbol],
                platformFeePercent = settings.platformFeePercent,
                details = details[alert.symbol],
                now = now
            )
            if (eval.updated != alert) alertDao.update(eval.updated)

            if (!eval.shouldNotify) continue

            // Always log to history.
            alertEventDao.insert(
                AlertEventEntity(
                    alertId = alert.id,
                    symbol = alert.symbol,
                    type = alert.type.name,
                    message = eval.message ?: "Alert triggered",
                    priceAtTrigger = quote.price,
                    threshold = alert.threshold.takeIf { it != 0.0 },
                    firedAtMillis = now
                )
            )

            // Notification gating: respect quiet hours + market-hours-only.
            val marketOnly = alert.marketHoursOnly ?: settings.marketHoursOnly
            val suppress = isQuietHours || (marketOnly && !isMarketOpen)

            if (!suppress) {
                val title = "Stock Alert: ${alert.symbol}"
                val body = buildString {
                    append(eval.message ?: "Alert triggered")
                    append("\nCurrent price: ${"%.2f".format(quote.price)}")
                    if (!alert.notes.isNullOrBlank()) {
                        append("\n\u270e ${alert.notes}")
                    }
                }
                NotificationHelper.show(
                    context = applicationContext,
                    notificationId = notifId++,
                    symbol = alert.symbol,
                    title = title,
                    body = body
                )
            }

            // Auto-disable mode: fire once, then turn the alert off.
            if (alert.autoDisableAfterFire) {
                alertDao.setEnabled(alert.id, false)
            }
        }

        // Keep history bounded.
        alertEventDao.trim(keep = 500)
        return Result.success()
    }
}

private fun AlertEntity.isActiveAt(nowMillis: Long): Boolean {
    val until = snoozedUntilMillis ?: return enabled
    return enabled && nowMillis >= until
}

private suspend fun fireAutomaticEarningsAlerts(
    context: Context,
    alertEventDao: AlertEventDao,
    details: Map<String, StockDetails>,
    now: Long,
    isQuietHours: Boolean,
    startingNotificationId: Int
): Int {
    var notifId = startingNotificationId
    val nowKenya = ZonedDateTime.ofInstant(
        Instant.ofEpochMilli(now),
        MarketClock.KENYA
    )
    val today = nowKenya.toLocalDate()
    val dayStart = today.atStartOfDay(MarketClock.KENYA).toInstant().toEpochMilli()
    val dayEnd = today.plusDays(1).atStartOfDay(MarketClock.KENYA).toInstant().toEpochMilli() - 1

    for ((symbol, detail) in details) {
        val eps = detail.nextEarningsEpochSeconds ?: continue
        val releaseAt = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(eps),
            MarketClock.KENYA
        )
        val releaseDate = releaseAt.toLocalDate()

        val (threshold, message) = when {
            today == releaseDate.minusDays(1) -> {
                AUTO_EARNINGS_DAY_BEFORE_THRESHOLD to
                    "$symbol results tomorrow - ${MarketClock.formatKenyaLong(eps)}"
            }
            today == releaseDate && !nowKenya.toInstant().isBefore(releaseAt.toInstant()) -> {
                AUTO_EARNINGS_RELEASE_THRESHOLD to
                    "$symbol results release time - ${MarketClock.formatKenyaLong(eps)}"
            }
            else -> continue
        }

        val alreadyLogged = alertEventDao.countForSymbolTypeThresholdInWindow(
            symbol = symbol,
            type = AlertType.EARNINGS_REMINDER.name,
            threshold = threshold,
            fromMillis = dayStart,
            toMillis = dayEnd
        ) > 0
        if (alreadyLogged) continue

        alertEventDao.insert(
            AlertEventEntity(
                alertId = null,
                symbol = symbol,
                type = AlertType.EARNINGS_REMINDER.name,
                message = message,
                priceAtTrigger = null,
                threshold = threshold,
                firedAtMillis = now
            )
        )

        if (!isQuietHours) {
            NotificationHelper.show(
                context = context,
                notificationId = notifId++,
                symbol = symbol,
                title = "Results Alert: $symbol",
                body = message
            )
        }
    }

    return notifId
}

private fun AlertType.requiresDetails(): Boolean = when (this) {
    AlertType.EARNINGS_REMINDER,
    AlertType.FIFTY_TWO_WEEK_HIGH,
    AlertType.FIFTY_TWO_WEEK_LOW,
    AlertType.MA200_CROSS_UP,
    AlertType.MA200_CROSS_DOWN,
    AlertType.VOLUME_SPIKE,
    AlertType.ANALYST_TARGET_REACH -> true
    else -> false
}

private const val AUTO_EARNINGS_RELEASE_THRESHOLD = 0.0
private const val AUTO_EARNINGS_DAY_BEFORE_THRESHOLD = 1.0
