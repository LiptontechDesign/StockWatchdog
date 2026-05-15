package com.stockwatchdog.app.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.stockwatchdog.app.BuildConfig
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.DipTrackerEntity
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
import com.stockwatchdog.app.data.db.entities.WatchlistItemEntity
import com.stockwatchdog.app.data.prefs.UserSettings
import com.stockwatchdog.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Mirrors the phone's alert configuration to Firestore so Firebase Cloud
 * Functions can evaluate alerts and send FCM even when the app is closed.
 *
 * Local Room remains the source of truth for the UI. Firestore is the cloud
 * execution copy for closed-app notifications.
 */
object CloudAlertSync {
    private const val TAG = "CloudAlertSync"
    private const val COLLECTION = "alertUsers"

    private var syncScope: CoroutineScope? = null

    fun start(context: Context, container: AppContainer) {
        if (syncScope != null) return
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        syncScope = scope

        val auth = FirebaseAuth.getInstance()
        val existing = auth.currentUser
        if (existing != null) {
            startCollector(scope, appContext, container, existing.uid)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid == null) {
                    Log.w(TAG, "Anonymous Firebase user missing uid")
                    return@addOnSuccessListener
                }
                FirebaseCrashlytics.getInstance().setCustomKey("firebase_auth_uid_ready", true)
                startCollector(scope, appContext, container, uid)
            }
            .addOnFailureListener { error ->
                FirebaseCrashlytics.getInstance().setCustomKey("firebase_auth_uid_ready", false)
                Log.w(TAG, "Anonymous Firebase sign-in failed: ${error.message}", error)
                syncScope = null
                scope.launch {
                    container.settingsRepository.setFirebaseMessagingTopicsReady(
                        ready = false,
                        error = "Cloud sync needs Firebase Anonymous Auth enabled."
                    )
                }
            }
    }

    fun stop() {
        syncScope?.cancel()
        syncScope = null
    }

    private fun startCollector(
        scope: CoroutineScope,
        context: Context,
        container: AppContainer,
        uid: String
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection(COLLECTION).document(uid)

        scope.launch {
            combine(
                container.settingsRepository.settings,
                container.database.alertDao().observeAll(),
                container.database.positionLotDao().observeAll(),
                container.database.watchlistDao().observeAll(),
                container.database.dipTrackerDao().observeAll()
            ) { settings, alerts, lots, watchlist, dips ->
                CloudAlertSnapshot(
                    uid = uid,
                    settings = settings,
                    alerts = alerts,
                    lots = lots,
                    watchlist = watchlist,
                    dips = dips
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    delay(600)
                    val payload = snapshot.toFirestorePayload(context)
                    userDoc.set(payload, SetOptions.merge())
                        .addOnSuccessListener {
                            FirebaseCrashlytics.getInstance().setCustomKey("cloud_alert_sync_ready", true)
                            scope.launch {
                                container.settingsRepository.setFirebaseMessagingTopicsReady(true)
                            }
                        }
                        .addOnFailureListener { error ->
                            FirebaseCrashlytics.getInstance().setCustomKey("cloud_alert_sync_ready", false)
                            Log.w(TAG, "Cloud alert sync failed: ${error.message}", error)
                            scope.launch {
                                container.settingsRepository.setFirebaseMessagingTopicsReady(
                                    ready = false,
                                    error = "Cloud sync failed: ${error.message ?: "check Firebase setup"}"
                                )
                            }
                        }
                }
        }
    }

    private data class CloudAlertSnapshot(
        val uid: String,
        val settings: UserSettings,
        val alerts: List<AlertEntity>,
        val lots: List<PositionLotEntity>,
        val watchlist: List<WatchlistItemEntity>,
        val dips: List<DipTrackerEntity>
    )

    private fun CloudAlertSnapshot.toFirestorePayload(context: Context): Map<String, Any?> {
        val entryPrices = computeEntryPrices(lots, watchlist)
        val trackedSymbols = (
            alerts.map { it.symbol } +
                watchlist.map { it.symbol } +
                dips.map { it.symbol }
            )
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()

        return mapOf(
            "uid" to uid,
            "packageName" to context.packageName,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "appVersionName" to BuildConfig.VERSION_NAME,
            "updatedAtMillis" to System.currentTimeMillis(),
            "active" to (
                settings.notificationsEnabled &&
                    settings.firebasePushEnabled &&
                    settings.firebaseMessagingToken.isNotBlank()
                ),
            "fcmToken" to settings.firebaseMessagingToken,
            "notificationsEnabled" to settings.notificationsEnabled,
            "firebasePushEnabled" to settings.firebasePushEnabled,
            "marketHoursOnly" to settings.marketHoursOnly,
            "quietHoursEnabled" to settings.quietHoursEnabled,
            "quietHoursStartMinutes" to settings.quietHoursStartMinutes,
            "quietHoursEndMinutes" to settings.quietHoursEndMinutes,
            "platformFeePercent" to settings.platformFeePercent,
            "entryPrices" to entryPrices,
            "trackedSymbols" to trackedSymbols,
            "alerts" to alerts.map { it.toCloudMap(entryPrices[it.symbol.trim().uppercase()]) }
        )
    }

    private fun AlertEntity.toCloudMap(entryPrice: Double?): Map<String, Any?> =
        mapOf(
            "id" to id.toString(),
            "symbol" to symbol.trim().uppercase(),
            "type" to type.name,
            "threshold" to threshold,
            "enabled" to enabled,
            "createdAtMillis" to createdAtMillis,
            "lastTriggeredAtMillis" to lastTriggeredAtMillis,
            "lastCrossingState" to lastCrossingState,
            "lastPercentTriggerDate" to lastPercentTriggerDate,
            "autoDisableAfterFire" to autoDisableAfterFire,
            "snoozedUntilMillis" to snoozedUntilMillis,
            "notes" to notes,
            "marketHoursOnly" to marketHoursOnly,
            "lastPrice" to lastPrice,
            "entryPrice" to entryPrice
        )

    private fun computeEntryPrices(
        lots: List<PositionLotEntity>,
        watchlist: List<WatchlistItemEntity>
    ): Map<String, Double> {
        val fromLots = lots
            .groupBy { it.symbol.trim().uppercase() }
            .mapNotNull { (symbol, symbolLots) ->
                val invested = symbolLots.sumOf { it.amountInvested }
                val quantity = symbolLots.sumOf {
                    if (it.entryPrice > 0.0) it.amountInvested / it.entryPrice else 0.0
                }
                if (symbol.isNotBlank() && quantity > 0.0) symbol to invested / quantity else null
            }
            .toMap()

        val fallback = watchlist.mapNotNull { item ->
            val symbol = item.symbol.trim().uppercase()
            val entry = item.entryPrice
            if (symbol.isNotBlank() && entry != null && entry > 0.0 && symbol !in fromLots) {
                symbol to entry
            } else {
                null
            }
        }

        return fromLots + fallback
    }
}
