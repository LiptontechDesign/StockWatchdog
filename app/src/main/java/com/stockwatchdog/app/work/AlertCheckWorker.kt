package com.stockwatchdog.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stockwatchdog.app.StockWatchdogApp
import com.stockwatchdog.app.domain.AlertEvaluator
import com.stockwatchdog.app.domain.DataResult
import com.stockwatchdog.app.notifications.NotificationHelper
import kotlinx.coroutines.flow.first

/**
 * Periodic worker that:
 *  1. Loads all enabled alerts.
 *  2. Fetches a fresh quote for each distinct symbol (respecting the API provider).
 *  3. Evaluates each alert, fires notifications for new transitions,
 *     and writes updated alert state back to Room to prevent re-firing.
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
        val watchlistDao = container.database.watchlistDao()
        val positionLotDao = container.database.positionLotDao()
        val enabled = alertDao.getAllEnabled()
        if (enabled.isEmpty()) return Result.success()

        val symbols = enabled.map { it.symbol }.distinct()
        val results = container.marketDataRepository.refreshQuotes(symbols)
        val entryPrices: Map<String, Double?> = symbols.associateWith { sym ->
            // Prefer the new multi-lot position: use the weighted average
            // entry. Fall back to the legacy watchlist.entryPrice field for
            // users who haven't migrated their tracked position yet.
            val lots = positionLotDao.getBySymbol(sym)
            val totalInvested = lots.sumOf { it.amountInvested }
            val totalQty = lots.sumOf {
                if (it.entryPrice > 0) it.amountInvested / it.entryPrice else 0.0
            }
            if (totalQty > 0) totalInvested / totalQty
            else watchlistDao.getBySymbol(sym)?.entryPrice
        }

        var notifId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        for (alert in enabled) {
            val res = results[alert.symbol] ?: continue
            if (res !is DataResult.Success) continue
            val quote = res.value
            val eval = AlertEvaluator.evaluate(
                alert = alert,
                quote = quote,
                entryPrice = entryPrices[alert.symbol],
                platformFeePercent = settings.platformFeePercent
            )
            if (eval.updated != alert) alertDao.update(eval.updated)
            if (eval.shouldNotify) {
                val title = "Stock Alert: ${alert.symbol}"
                val body = (eval.message ?: "Alert triggered") +
                    "\nCurrent price: ${"%.2f".format(quote.price)}"
                NotificationHelper.show(
                    context = applicationContext,
                    notificationId = notifId++,
                    symbol = alert.symbol,
                    title = title,
                    body = body
                )
            }
        }
        return Result.success()
    }
}
