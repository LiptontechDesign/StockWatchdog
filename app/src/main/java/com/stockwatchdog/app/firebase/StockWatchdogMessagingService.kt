package com.stockwatchdog.app.firebase

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stockwatchdog.app.data.prefs.SettingsRepository
import com.stockwatchdog.app.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class StockWatchdogMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val repo = SettingsRepository(applicationContext)
        runBlocking {
            repo.saveFirebaseMessagingToken(token)
        }
        FirebaseServices.refreshMessaging(applicationContext)
        FirebaseCrashlytics.getInstance().setCustomKey("fcm_token_ready", true)
        FirebaseCrashlytics.getInstance().setCustomKey("fcm_token_length", token.length)
        Log.i(TAG, "FCM token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val repo = SettingsRepository(applicationContext)
        val settings = runBlocking { repo.settings.first() }
        if (!settings.notificationsEnabled || !settings.firebasePushEnabled) {
            return
        }

        val symbol = message.data["symbol"]
            ?: message.data["ticker"]
            ?: ""
        val route = message.data["route"]
            ?: message.data["screen"]
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Stock Watchdog"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: return

        val notificationId = (message.messageId ?: "$title:$body:${System.currentTimeMillis()}")
            .hashCode() and Int.MAX_VALUE

        runBlocking {
            repo.setFirebaseLastMessageAt()
        }

        NotificationHelper.show(
            context = this,
            notificationId = notificationId,
            symbol = symbol,
            title = title,
            body = body,
            route = route
        )
    }

    private companion object {
        const val TAG = "StockWatchdogFCM"
    }
}
