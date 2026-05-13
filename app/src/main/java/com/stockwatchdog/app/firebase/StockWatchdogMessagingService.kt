package com.stockwatchdog.app.firebase

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stockwatchdog.app.notifications.NotificationHelper

class StockWatchdogMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseCrashlytics.getInstance().setCustomKey("fcm_token_ready", true)
        FirebaseCrashlytics.getInstance().setCustomKey("fcm_token_length", token.length)
        Log.i(TAG, "FCM token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val symbol = message.data["symbol"]
            ?: message.data["ticker"]
            ?: "watchlist"
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Stock Watchdog"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: return

        val notificationId = (message.messageId ?: "$title:$body:${System.currentTimeMillis()}")
            .hashCode() and Int.MAX_VALUE

        NotificationHelper.show(
            context = this,
            notificationId = notificationId,
            symbol = symbol,
            title = title,
            body = body
        )
    }

    private companion object {
        const val TAG = "StockWatchdogFCM"
    }
}
