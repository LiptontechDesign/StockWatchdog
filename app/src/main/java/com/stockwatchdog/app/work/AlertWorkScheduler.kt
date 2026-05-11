package com.stockwatchdog.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AlertWorkScheduler {

    private const val WORK_NAME = "stockwatchdog-alert-check"
    private const val IMMEDIATE_WORK_NAME = "stockwatchdog-alert-check-now"

    /**
     * Schedules periodic alert checks. Android enforces a 15-minute minimum
     * interval for PeriodicWorkRequest; we respect that and also provide
     * 30 and 60 minute options from Settings.
     */
    fun schedule(context: Context, intervalMinutes: Int) {
        val safeMinutes = intervalMinutes.coerceAtLeast(15).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(
            safeMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(safeMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        val immediateRequest = OneTimeWorkRequestBuilder<AlertCheckWorker>()
            .setConstraints(constraints)
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
}
