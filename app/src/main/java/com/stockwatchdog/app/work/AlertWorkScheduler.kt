package com.stockwatchdog.app.work

import android.content.Context
import androidx.work.WorkManager

object AlertWorkScheduler {

    private const val WORK_NAME = "stockwatchdog-alert-check"
    private const val IMMEDIATE_WORK_NAME = "stockwatchdog-alert-check-now"
    const val DEFAULT_INTERVAL_MINUTES = 30

    /**
     * Closed-app alerts now come from Firebase Cloud Messaging. Keep this
     * method as a compatibility cleanup hook for old installs that may still
     * have WorkManager jobs registered from earlier builds.
     */
    fun schedule(context: Context, @Suppress("UNUSED_PARAMETER") intervalMinutes: Int) {
        cancel(context)
    }

    suspend fun scheduleFromSettings(context: Context) {
        // Closed-app alerts are Firebase cloud pushes now. Always cancel old
        // local polling work so Android background limits do not decide alerts.
        cancel(context)
    }

    fun runNow(context: Context) {
        cancel(context)
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        workManager.cancelAllWorkByTag(AlertCheckWorker::class.java.name)
        workManager.cancelAllWork()
        workManager.pruneWork()
    }
}
