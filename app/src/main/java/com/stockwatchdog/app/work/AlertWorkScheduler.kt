package com.stockwatchdog.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.stockwatchdog.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object AlertWorkScheduler {

    private const val WORK_NAME = "stockwatchdog-alert-check"
    private const val IMMEDIATE_WORK_NAME = "stockwatchdog-alert-check-now"
    const val DEFAULT_INTERVAL_MINUTES = 30

    /**
     * Schedules periodic alert checks. Android enforces a 15-minute minimum
     * interval for PeriodicWorkRequest; we respect that and also provide
     * 30 and 60 minute options from Settings.
     */
    fun schedule(context: Context, intervalMinutes: Int) {
        val safeMinutes = intervalMinutes.coerceAtLeast(15).toLong()

        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(
            safeMinutes, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints())
            .setInitialDelay(safeMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        runNow(context)
    }

    suspend fun scheduleFromSettings(context: Context) {
        val settings = SettingsRepository(context.applicationContext).settings.first()
        if (settings.notificationsEnabled) {
            schedule(context, settings.intervalMinutes)
        } else {
            cancel(context)
        }
    }

    fun runNow(context: Context) {
        val immediateRequest = OneTimeWorkRequestBuilder<AlertCheckWorker>()
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
