package com.stockwatchdog.app.firebase

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object FirebaseServices {
    private const val TAG = "FirebaseServices"
    private const val REMOTE_CONFIG_FETCH_SECONDS = 12L * 60L * 60L

    fun configure(context: Context) {
        runCatching {
            FirebaseApp.initializeApp(context)

            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCrashlyticsCollectionEnabled(true)
            crashlytics.setCustomKey("app_mode", "personal")

            val analytics = FirebaseAnalytics.getInstance(context)
            analytics.setAnalyticsCollectionEnabled(true)
            analytics.logEvent(
                FirebaseAnalytics.Event.APP_OPEN,
                Bundle().apply { putString("source", "application_start") }
            )

            configureRemoteConfig(crashlytics)
            configureMessaging(crashlytics)
        }.onFailure {
            Log.w(TAG, "Firebase startup skipped: ${it.message}")
        }
    }

    private fun configureRemoteConfig(crashlytics: FirebaseCrashlytics) {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(REMOTE_CONFIG_FETCH_SECONDS)
            .build()

        remoteConfig.setConfigSettingsAsync(settings)
        remoteConfig.setDefaultsAsync(
            mapOf(
                "enable_sec_edgar" to true,
                "enable_fmp_financials" to true,
                "enable_finnhub_recommendations" to true,
                "enable_push_alerts" to true,
                "details_cache_hours" to 12L,
                "market_refresh_minutes" to 15L
            )
        )
        remoteConfig.fetchAndActivate()
            .addOnSuccessListener { activated ->
                crashlytics.setCustomKey("remote_config_active", activated)
            }
            .addOnFailureListener {
                crashlytics.setCustomKey("remote_config_error", it.message ?: "unknown")
                Log.w(TAG, "Remote Config fetch failed: ${it.message}")
            }
    }

    private fun configureMessaging(crashlytics: FirebaseCrashlytics) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener {
                crashlytics.setCustomKey("fcm_token_ready", true)
            }
            .addOnFailureListener {
                crashlytics.setCustomKey("fcm_token_ready", false)
                Log.w(TAG, "FCM token unavailable: ${it.message}")
            }
    }
}
