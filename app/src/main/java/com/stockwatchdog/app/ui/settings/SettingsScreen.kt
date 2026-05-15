package com.stockwatchdog.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.data.prefs.ApiProvider
import com.stockwatchdog.app.data.prefs.ThemeMode
import com.stockwatchdog.app.data.prefs.UserSettings
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.components.CompactActionRow
import com.stockwatchdog.app.util.MarketClock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val localContext = LocalContext.current
    val ctx = localContext.applicationContext
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsRepository, ctx) }
        }
    )
    val s by vm.state.collectAsStateWithLifecycle()
    val phoneNotificationsAllowed =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            (
                NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
                    ContextCompat.checkSelfPermission(
                        ctx,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.enableCloudNotifications()
    }
    val allowCloudAlerts: () -> Unit = {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !phoneNotificationsAllowed
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.enableCloudNotifications()
        }
    }
    var editingTwelve by rememberSaveable { mutableStateOf(false) }
    var editingAlpha by rememberSaveable { mutableStateOf(false) }
    var editingFinnhub by rememberSaveable { mutableStateOf(false) }
    var editingFmp by rememberSaveable { mutableStateOf(false) }
    var twelveDraft by rememberSaveable { mutableStateOf("") }
    var alphaDraft by rememberSaveable { mutableStateOf("") }
    var finnhubDraft by rememberSaveable { mutableStateOf("") }
    var fmpDraft by rememberSaveable { mutableStateOf("") }
    var editingFee by rememberSaveable { mutableStateOf(false) }
    var feeDraft by rememberSaveable { mutableStateOf("") }

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
            Text(
                "Auto rotates between Finnhub, Twelve Data, Yahoo Finance and " +
                    "Alpha Vantage for prices. FMP and official SEC EDGAR filings " +
                    "are used for deeper financial statements when available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = s.provider == ApiProvider.AUTO,
                    onClick = { vm.setProvider(ApiProvider.AUTO) },
                    label = { Text("Auto") }
                )
                FilterChip(
                    selected = s.provider == ApiProvider.FINNHUB,
                    onClick = { vm.setProvider(ApiProvider.FINNHUB) },
                    label = { Text("Finnhub") }
                )
                FilterChip(
                    selected = s.provider == ApiProvider.TWELVE_DATA,
                    onClick = { vm.setProvider(ApiProvider.TWELVE_DATA) },
                    label = { Text("Twelve Data") }
                )
                FilterChip(
                    selected = s.provider == ApiProvider.YAHOO,
                    onClick = { vm.setProvider(ApiProvider.YAHOO) },
                    label = { Text("Yahoo") }
                )
                FilterChip(
                    selected = s.provider == ApiProvider.ALPHA_VANTAGE,
                    onClick = { vm.setProvider(ApiProvider.ALPHA_VANTAGE) },
                    label = { Text("Alpha Vantage") }
                )
            }
            Spacer(Modifier.height(12.dp))
            ApiKeySetting(
                label = "FMP API key",
                hasSavedValue = s.fmpKey.isNotBlank(),
                isEditing = editingFmp,
                draftValue = fmpDraft,
                onOpenEditor = {
                    editingFmp = true
                    fmpDraft = ""
                },
                onDraftChange = { fmpDraft = it },
                onApply = {
                    vm.setFmpKey(fmpDraft)
                    fmpDraft = ""
                    editingFmp = false
                },
                onCancel = {
                    fmpDraft = ""
                    editingFmp = false
                }
            )
            Spacer(Modifier.height(8.dp))
            ApiKeySetting(
                label = "Finnhub API key",
                hasSavedValue = s.finnhubKey.isNotBlank(),
                isEditing = editingFinnhub,
                draftValue = finnhubDraft,
                onOpenEditor = {
                    editingFinnhub = true
                    finnhubDraft = ""
                },
                onDraftChange = { finnhubDraft = it },
                onApply = {
                    vm.setFinnhubKey(finnhubDraft)
                    finnhubDraft = ""
                    editingFinnhub = false
                },
                onCancel = {
                    finnhubDraft = ""
                    editingFinnhub = false
                }
            )
            Spacer(Modifier.height(8.dp))
            ApiKeySetting(
                label = "Twelve Data API key",
                hasSavedValue = s.twelveDataKey.isNotBlank(),
                isEditing = editingTwelve,
                draftValue = twelveDraft,
                onOpenEditor = {
                    editingTwelve = true
                    twelveDraft = ""
                },
                onDraftChange = { twelveDraft = it },
                onApply = {
                    vm.setTwelveKey(twelveDraft)
                    twelveDraft = ""
                    editingTwelve = false
                },
                onCancel = {
                    twelveDraft = ""
                    editingTwelve = false
                }
            )
            Spacer(Modifier.height(8.dp))
            ApiKeySetting(
                label = "Alpha Vantage API key",
                hasSavedValue = s.alphaVantageKey.isNotBlank(),
                isEditing = editingAlpha,
                draftValue = alphaDraft,
                onOpenEditor = {
                    editingAlpha = true
                    alphaDraft = ""
                },
                onDraftChange = { alphaDraft = it },
                onApply = {
                    vm.setAlphaKey(alphaDraft)
                    alphaDraft = ""
                    editingAlpha = false
                },
                onCancel = {
                    alphaDraft = ""
                    editingAlpha = false
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Yahoo Finance is used as a no-key backup when keyed providers " +
                    "are rate-limited.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Fees")
            FeeSetting(
                savedPercent = s.platformFeePercent,
                isEditing = editingFee,
                draftValue = feeDraft,
                onOpenEditor = {
                    editingFee = true
                    feeDraft = if (s.platformFeePercent == 0.0) "" else s.platformFeePercent.toString()
                },
                onDraftChange = { feeDraft = it },
                onApply = {
                    val parsed = feeDraft.replace(",", ".").toDoubleOrNull() ?: 0.0
                    vm.setPlatformFeePercent(parsed)
                    editingFee = false
                },
                onCancel = {
                    feeDraft = ""
                    editingFee = false
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Applied to position, watchlist, portfolio, and entry-based alert returns so your break-even includes platform and transaction costs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Notifications")
            CloudNotificationSetting(
                settings = s,
                phoneNotificationsAllowed = phoneNotificationsAllowed,
                onPrimaryAction = allowCloudAlerts
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Only notify while NYSE is open")
                    Text(
                        "Suppress notifications outside US market hours. " +
                            "Triggers are still saved to History.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = s.marketHoursOnly,
                    onCheckedChange = { vm.setMarketHoursOnly(it) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quiet hours (Kenya time)")
                    Text(
                        if (s.quietHoursEnabled)
                            "${com.stockwatchdog.app.util.MarketClock.formatHm(s.quietHoursStartMinutes)} \u2192 " +
                                "${com.stockwatchdog.app.util.MarketClock.formatHm(s.quietHoursEndMinutes)} EAT \u00b7 " +
                                "alerts go to History only"
                        else "Off \u00b7 alerts can fire at any time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = s.quietHoursEnabled,
                    onCheckedChange = { vm.setQuietHoursEnabled(it) }
                )
            }
            if (s.quietHoursEnabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Quick presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        Triple("22\u219207", 22 * 60, 7 * 60),
                        Triple("23\u219206", 23 * 60, 6 * 60),
                        Triple("00\u219208", 0, 8 * 60),
                        Triple("21\u219208", 21 * 60, 8 * 60)
                    )
                    presets.forEach { (label, start, end) ->
                        FilterChip(
                            selected = s.quietHoursStartMinutes == start &&
                                s.quietHoursEndMinutes == end,
                            onClick = { vm.setQuietHoursRange(start, end) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionHeader("Theme")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudNotificationSetting(
    settings: UserSettings,
    phoneNotificationsAllowed: Boolean,
    onPrimaryAction: () -> Unit
) {
    val tokenReady = settings.firebaseMessagingToken.isNotBlank()
    val cloudSyncReady = settings.firebaseMessagingTopicsReady
    val ready = settings.notificationsEnabled &&
        settings.firebasePushEnabled &&
        phoneNotificationsAllowed &&
        tokenReady &&
        cloudSyncReady
    val statusIsProblem = settings.firebaseMessagingLastError.contains("needs", ignoreCase = true) ||
        settings.firebaseMessagingLastError.contains("failed", ignoreCase = true) ||
        settings.firebaseMessagingLastError.contains("unavailable", ignoreCase = true)
    val statusColor = when {
        ready -> MaterialTheme.colorScheme.primary
        statusIsProblem -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when {
        ready -> "Configuration successful. Closed-app cloud alerts are ready."
        !phoneNotificationsAllowed -> "Waiting for phone notification permission."
        !settings.notificationsEnabled || !settings.firebasePushEnabled -> "Cloud alerts are off in the app."
        !tokenReady -> "Connecting this phone to Firebase and requesting an FCM token."
        !cloudSyncReady -> settings.firebaseMessagingLastError.ifBlank {
            "Syncing your alert rules to Firestore for the free GitHub checker."
        }
        else -> "Checking cloud alert setup."
    }
    val buttonText = when {
        !phoneNotificationsAllowed -> "Allow notifications"
        !settings.notificationsEnabled || !settings.firebasePushEnabled -> "Enable cloud alerts"
        ready -> "Refresh cloud alerts"
        else -> "Finish cloud setup"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Firebase cloud alerts")
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor
        )
        Spacer(Modifier.height(10.dp))
        CloudSetupStep(
            label = "Phone permission",
            value = if (phoneNotificationsAllowed) "Allowed" else "Needs approval",
            complete = phoneNotificationsAllowed
        )
        CloudSetupStep(
            label = "App cloud alerts",
            value = if (settings.notificationsEnabled && settings.firebasePushEnabled) "Enabled" else "Off",
            complete = settings.notificationsEnabled && settings.firebasePushEnabled
        )
        CloudSetupStep(
            label = "Firebase token",
            value = if (tokenReady) "Saved (${settings.firebaseMessagingToken.takeLast(6)})" else "Connecting",
            complete = tokenReady
        )
        CloudSetupStep(
            label = "Alert rule sync",
            value = if (cloudSyncReady) "Synced" else "Pending",
            complete = cloudSyncReady
        )
        CloudSetupStep(
            label = "Free checker",
            value = if (ready) "GitHub Actions scheduled" else "Waits for sync",
            complete = ready
        )
        Spacer(Modifier.height(8.dp))
        Text(
            buildString {
                append("Last push: ")
                append(formatFirebaseTime(settings.firebaseLastMessageAtMillis))
                if (!ready && settings.firebaseMessagingLastError.isNotBlank()) {
                    append("\n")
                    append(settings.firebaseMessagingLastError)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onPrimaryAction) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun CloudSetupStep(
    label: String,
    value: String,
    complete: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (complete) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ApiKeySetting(
    label: String,
    hasSavedValue: Boolean,
    isEditing: Boolean,
    draftValue: String,
    onOpenEditor: () -> Unit,
    onDraftChange: (String) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    if (isEditing) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draftValue,
                onValueChange = onDraftChange,
                label = { Text(label) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = { Text("Stored on this phone and hidden after saving.") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            CompactActionRow(
                primaryText = "Save key",
                onPrimary = onApply,
                enabled = draftValue.isNotBlank(),
                onCancel = onCancel
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (hasSavedValue) "Saved and hidden" else "Not set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenEditor) {
                Text(if (hasSavedValue) "Replace key" else "Paste key")
            }
        }
    }
}

@Composable
private fun FeeSetting(
    savedPercent: Double,
    isEditing: Boolean,
    draftValue: String,
    onOpenEditor: () -> Unit,
    onDraftChange: (String) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    if (isEditing) {
        val valid = draftValue.replace(",", ".").toDoubleOrNull()?.let { it >= 0 } == true
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draftValue,
                onValueChange = onDraftChange,
                label = { Text("Platform fee %") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text("Used when showing net return after fees.") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            CompactActionRow(
                primaryText = "Save fee",
                onPrimary = onApply,
                enabled = valid,
                onCancel = onCancel
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Platform fee adjustment",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (savedPercent > 0.0) "${"%.2f".format(savedPercent)}% applied to net returns" else "0.00% (off)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenEditor) {
                Text(if (savedPercent > 0.0) "Edit" else "Set fee")
            }
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

private val FirebaseStatusTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.US)

private fun formatFirebaseTime(millis: Long): String =
    if (millis <= 0L) {
        "never"
    } else {
        Instant.ofEpochMilli(millis)
            .atZone(MarketClock.KENYA)
            .format(FirebaseStatusTimeFormat) + " EAT"
    }
