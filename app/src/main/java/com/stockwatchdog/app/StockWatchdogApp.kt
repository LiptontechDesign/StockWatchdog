package com.stockwatchdog.app

import android.app.Application
import androidx.work.Configuration
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.notifications.NotificationHelper
import com.stockwatchdog.app.work.AlertWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StockWatchdogApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)

        // Apply the user's configured monitoring interval on startup.
        appScope.launch {
            val settings = container.settingsRepository.settings.first()
            AlertWorkScheduler.schedule(applicationContext, settings.intervalMinutes)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
