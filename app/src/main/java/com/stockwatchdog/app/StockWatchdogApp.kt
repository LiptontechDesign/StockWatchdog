package com.stockwatchdog.app

import android.app.Application
import androidx.work.Configuration
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.firebase.FirebaseServices
import com.stockwatchdog.app.notifications.NotificationHelper
import com.stockwatchdog.app.work.AlertWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StockWatchdogApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        FirebaseServices.configure(this)
        container = AppContainer(this)
        FirebaseServices.startCloudAlertSync(this, container)
        NotificationHelper.ensureChannel(this)

        // Apply the user's configured monitoring interval on startup.
        appScope.launch {
            AlertWorkScheduler.scheduleFromSettings(applicationContext)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
