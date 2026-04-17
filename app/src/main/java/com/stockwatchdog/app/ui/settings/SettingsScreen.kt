package com.stockwatchdog.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.data.prefs.ApiProvider
import com.stockwatchdog.app.data.prefs.ThemeMode
import com.stockwatchdog.app.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val ctx = LocalContext.current.applicationContext
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, ctx) }
        }
    )
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.SemiBold) }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SectionHeader("Data provider")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.provider == ApiProvider.TWELVE_DATA,
                    onClick = { vm.setProvider(ApiProvider.TWELVE_DATA) },
                    label = { Text("Twelve Data") }
                )
                FilterChip(
                    selected = s.provider == ApiProvider.ALPHA_VANTAGE,
                    onClick = { vm.setProvider(ApiProvider.ALPHA_VANTAGE) },
                    label = { Text("Alpha Vantage") }
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = s.twelveDataKey,
                onValueChange = { vm.setTwelveKey(it) },
                label = { Text("Twelve Data API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.alphaVantageKey,
                onValueChange = { vm.setAlphaKey(it) },
                label = { Text("Alpha Vantage API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Monitoring interval")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    FilterChip(
                        selected = s.intervalMinutes == m,
                        onClick = { vm.setInterval(m) },
                        label = { Text("$m min") }
                    )
                }
            }
            Text(
                "Android enforces a 15-minute minimum for periodic background work. " +
                    "Longer intervals save more battery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Notifications")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable price alert notifications")
                Switch(
                    checked = s.notificationsEnabled,
                    onCheckedChange = { vm.setNotificationsEnabled(it) }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { mode ->
                    FilterChip(
                        selected = s.themeMode == mode,
                        onClick = { vm.setTheme(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Samsung battery tip")
            Text(
                "Samsung's power-saving features can pause background work. For reliable " +
                    "alerts:\n\n" +
                    "• Settings → Battery → Background usage limits → Never sleeping apps → add Stock Watchdog.\n" +
                    "• Settings → Apps → Stock Watchdog → Battery → Unrestricted.\n" +
                    "• Keep \"Allow background activity\" turned on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
