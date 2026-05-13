package com.stockwatchdog.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.stockwatchdog.app.ui.navigation.AppNavHost
import com.stockwatchdog.app.ui.navigation.Routes
import com.stockwatchdog.app.ui.navigation.TopLevel
import com.stockwatchdog.app.ui.theme.StockWatchdogTheme

class MainActivity : ComponentActivity() {

    private val deepLinkRoute = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as StockWatchdogApp).container
        deepLinkRoute.value = parseDeepLinkRoute(intent)

        setContent {
            val settings by container.settingsRepository.settings.collectAsState(
                initial = com.stockwatchdog.app.data.prefs.UserSettings()
            )
            StockWatchdogTheme(themeMode = settings.themeMode) {
                AppNavHost(
                    container = container,
                    initialDeepLinkRoute = deepLinkRoute.value,
                    onDeepLinkConsumed = { deepLinkRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseDeepLinkRoute(intent)?.let { deepLinkRoute.value = it }
    }

    private fun parseDeepLinkRoute(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "stockwatchdog") return null
        return when (data.host) {
            "ticker" -> data.lastPathSegment
                ?.takeIf { it.isNotBlank() }
                ?.let { Routes.detail(it.uppercase()) }
            "watchlist" -> TopLevel.Watchlist.route
            "portfolio" -> TopLevel.Portfolio.route
            "dip" -> TopLevel.Dip.route
            "alerts" -> TopLevel.Alerts.route
            "settings" -> TopLevel.Settings.route
            else -> null
        }
    }
}
