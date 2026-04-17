package com.stockwatchdog.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stockwatchdog.app.MainActivity
import com.stockwatchdog.app.R

object NotificationHelper {

    const val CHANNEL_ALERTS = "alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.notif_channel_alerts),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_alerts_desc)
                    setShowBadge(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    fun show(
        context: Context,
        notificationId: Int,
        symbol: String,
        title: String,
        body: String
    ) {
        ensureChannel(context)

        val deepLink = Uri.parse("stockwatchdog://ticker/$symbol")
        val intent = Intent(Intent.ACTION_VIEW, deepLink, context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(notificationId, notif)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted yet; silently skip.
            }
        }
    }
}
