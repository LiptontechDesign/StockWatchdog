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
import com.stockwatchdog.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FirebaseServices {
    const val TOPIC_PERSONAL_ALERTS = "stockwatchdog_personal_alerts"
    const val TOPIC_MARKET_STATUS = "stockwatchdog_market_status"

    private const val TAG = "FirebaseServices"
    private const val REMOTE_CONFIG_FETCH_SECONDS = 12L * 60L * 60L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            refreshMessaging(context)
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

    fun refreshMessaging(context: Context) {
        val appContext = context.applicationContext
        val repo = SettingsRepository(appContext)
        val crashlytics = FirebaseCrashlytics.getInstance()
        val messaging = FirebaseMessaging.getInstance().apply {
            isAutoInitEnabled = true
        }

        messaging.token
            .addOnSuccessListener {
                scope.launch {
                    repo.saveFirebaseMessagingToken(it)
                }
                crashlytics.setCustomKey("fcm_token_ready", true)
                crashlytics.setCustomKey("fcm_token_length", it.length)
            }
            .addOnFailureListener {
                crashlytics.setCustomKey("fcm_token_ready", false)
                scope.launch {
                    repo.setFirebaseMessagingTopicsReady(false, it.message ?: "Token unavailable")
                }
                Log.w(TAG, "FCM token unavailable: ${it.message}")
            }

        subscribeToTopics(messaging, repo, crashlytics)
    }

    private fun subscribeToTopics(
        messaging: FirebaseMessaging,
        repo: SettingsRepository,
        crashlytics: FirebaseCrashlytics
    ) {
        val topics = listOf(TOPIC_PERSONAL_ALERTS, TOPIC_MARKET_STATUS)
        var remaining = topics.size
        var failed = false

        topics.forEach { topic ->
            messaging.subscribeToTopic(topic)
                .addOnSuccessListener {
                    remaining -= 1
                    if (remaining == 0 && !failed) {
                        crashlytics.setCustomKey("fcm_topics_ready", true)
                        scope.launch { repo.setFirebaseMessagingTopicsReady(true) }
                    }
                }
                .addOnFailureListener {
                    failed = true
                    crashlytics.setCustomKey("fcm_topics_ready", false)
                    val error = "Topic $topic: ${it.message ?: "failed"}"
                    scope.launch { repo.setFirebaseMessagingTopicsReady(false, error) }
                    Log.w(TAG, "FCM topic subscription failed: $error")
                }
        }
    }
}
