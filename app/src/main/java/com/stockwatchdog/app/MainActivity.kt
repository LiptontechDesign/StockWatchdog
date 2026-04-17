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
import com.stockwatchdog.app.ui.theme.StockWatchdogTheme

class MainActivity : ComponentActivity() {

    private val deepLinkSymbol = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as StockWatchdogApp).container
        deepLinkSymbol.value = parseTickerDeepLink(intent)

        setContent {
            val settings by container.settingsRepository.settings.collectAsState(
                initial = com.stockwatchdog.app.data.prefs.UserSettings()
            )
            StockWatchdogTheme(themeMode = settings.themeMode) {
                AppNavHost(
                    container = container,
                    initialDeepLinkSymbol = deepLinkSymbol.value,
                    onDeepLinkConsumed = { deepLinkSymbol.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseTickerDeepLink(intent)?.let { deepLinkSymbol.value = it }
    }

    private fun parseTickerDeepLink(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme == "stockwatchdog" && data.host == "ticker") {
            return data.lastPathSegment?.takeIf { it.isNotBlank() }
        }
        return null
    }
}
