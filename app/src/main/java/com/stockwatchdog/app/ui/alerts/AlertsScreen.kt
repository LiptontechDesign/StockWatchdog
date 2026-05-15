package com.stockwatchdog.app.ui.alerts

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertEventEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.detail.describeAlert
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen
import com.stockwatchdog.app.util.MarketClock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val SnoozedColor = Color(0xFF8E8E93)
private val EarningsColor = Color(0xFF6C5CE7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val ctx = LocalContext.current
    val vm: AlertsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AlertsViewModel(
                    alertDao = container.database.alertDao(),
                    eventDao = container.database.alertEventDao(),
                    priceCacheDao = container.database.priceCacheDao(),
                    marketDataRepository = container.marketDataRepository,
                    appContext = ctx.applicationContext
                )
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Notification permission tracking (Android 13+).
    var hasPostNotifPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPostNotifPermission = granted }

    LaunchedEffect(state.undoDeleteEntity) {
        val entity = state.undoDeleteEntity ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${entity.symbol} alert deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
        else vm.dismissUndoSnackbar()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alerts", fontWeight = FontWeight.SemiBold)
                        val active = state.rows.count { it.entity.enabled }
                        if (state.rows.isNotEmpty()) {
                            Text(
                                "$active active \u00b7 ${state.rows.size - active} paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTab == 1 && state.events.isNotEmpty()) {
                        IconButton(onClick = { vm.clearHistory() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Permission banner ─────────────────────────────────────
            if (!hasPostNotifPermission) {
                PermissionBanner(
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${ctx.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    }
                )
            }

            // ── Tabs ───────────────────────────────────────────────────
            if (selectedTab == 0) {
                AlertsActionHeader(
                    activeCount = state.rows.count { it.entity.enabled },
                    totalCount = state.rows.size,
                    onCreate = { vm.openPicker() }
                )
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active \u00b7 ${state.rows.size}") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("History \u00b7 ${state.events.size}") }
                )
            }

            when (selectedTab) {
                0 -> ActiveTab(
                    rows = state.rows,
                    onClickRow = { id -> vm.openEdit(id) },
                    onToggle = { id, on -> vm.toggle(id, on) },
                    onDelete = { id -> vm.confirmDelete(id) },
                    onSnooze1h = { id -> vm.snooze(id, 60 * 60 * 1000L) },
                    onSnooze1d = { id -> vm.snooze(id, 24 * 60 * 60 * 1000L) },
                    onUnsnooze = { id -> vm.unsnooze(id) },
                    onOpenSymbol = onOpenSymbol,
                    onCreate = { vm.openPicker() }
                )
                1 -> HistoryTab(
                    events = state.events,
                    onOpenSymbol = onOpenSymbol
                )
            }
        }
    }

    // ── Symbol picker ──────────────────────────────────────────────────
    if (state.pickerOpen) {
        AlertSymbolPickerSheet(
            query = state.pickerQuery,
            searching = state.pickerSearching,
            results = state.pickerResults,
            onQueryChange = vm::onPickerQueryChange,
            onPick = vm::pickSymbol,
            onDismiss = vm::closePicker
        )
    }

    // ── Create / edit dialog ───────────────────────────────────────────
    if (state.dialogOpen) {
        AlertEditDialog(
            isEditing = state.dialogEditing != null,
            symbolLocked = state.dialogEditing != null,
            symbol = state.dialogSymbol,
            type = state.dialogType,
            threshold = state.dialogThreshold,
            notes = state.dialogNotes,
            autoDisable = state.dialogAutoDisable,
            marketHoursOnly = state.dialogMarketHoursOnly,
            companyName = state.dialogCompanyName,
            currentPrice = state.dialogCurrentPrice,
            percentChange = state.dialogPercentChange,
            currency = state.dialogCurrency,
            // Per-symbol entry-price availability would need a lot lookup;
            // entry-relative chips are still shown but simply require a
            // position to fire. Keep the UI permissive here.
            hasEntryPrice = true,
            onSymbolChange = vm::onDialogSymbolChange,
            onTypeChange = vm::onDialogTypeChange,
            onThresholdChange = vm::onDialogThresholdChange,
            onNotesChange = vm::onDialogNotesChange,
            onAutoDisableChange = vm::onDialogAutoDisableChange,
            onMarketHoursOnlyChange = vm::onDialogMarketHoursOnlyChange,
            onSave = vm::saveDialog,
            onDismiss = vm::closeDialog
        )
    }

    // ── Confirm delete ─────────────────────────────────────────────────
    state.confirmDeleteId?.let { id ->
        val alert = state.rows.firstOrNull { it.entity.id == id }?.entity
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text("Delete alert?") },
            text = {
                Text(
                    if (alert != null) "Delete \"${describeAlert(alert)}\" for ${alert.symbol}?"
                    else "Delete this alert?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.delete(id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { vm.cancelDelete() }) { Text("Cancel") } }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
// Active tab
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun AlertsActionHeader(
    activeCount: Int,
    totalCount: Int,
    onCreate: () -> Unit
) {
    val pausedCount = (totalCount - activeCount).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Alert center",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (totalCount == 0) {
                    "Create price, results, target, volume, and trend alerts."
                } else {
                    "$activeCount active · $pausedCount paused"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Button(
            onClick = onCreate,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New alert", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveTab(
    rows: List<AlertRow>,
    onClickRow: (Long) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onSnooze1h: (Long) -> Unit,
    onSnooze1d: (Long) -> Unit,
    onUnsnooze: (Long) -> Unit,
    onOpenSymbol: (String) -> Unit,
    onCreate: () -> Unit
) {
    if (rows.isEmpty()) {
        EmptyAlertsState(onAdd = onCreate)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows, key = { it.entity.id }) { row ->
            AlertRowCard(
                row = row,
                onClickRow = { onClickRow(row.entity.id) },
                onToggle = { on -> onToggle(row.entity.id, on) },
                onDelete = { onDelete(row.entity.id) },
                onSnooze1h = { onSnooze1h(row.entity.id) },
                onSnooze1d = { onSnooze1d(row.entity.id) },
                onUnsnooze = { onUnsnooze(row.entity.id) },
                onOpenSymbol = { onOpenSymbol(row.entity.symbol) }
            )
        }
    }
}

@Composable
private fun AlertRowCard(
    row: AlertRow,
    onClickRow: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSnooze1h: () -> Unit,
    onSnooze1d: () -> Unit,
    onUnsnooze: () -> Unit,
    onOpenSymbol: () -> Unit
) {
    val a = row.entity
    val now = System.currentTimeMillis()
    val isSnoozed = (a.snoozedUntilMillis ?: 0L) > now
    val (icon, accent) = iconAndColorFor(a.type)
    val muted = !a.enabled || isSnoozed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickRow),
        colors = CardDefaults.cardColors(
            containerColor = if (muted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (muted) 0.dp else 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (muted) 0.18f else 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (muted) SnoozedColor else accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            a.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable(onClick = onOpenSymbol)
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusPill(row = row, isSnoozed = isSnoozed)
                    }
                    Text(
                        describeAlert(a),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = a.enabled,
                    onCheckedChange = onToggle
                )
                AlertOverflow(
                    isSnoozed = isSnoozed,
                    onEdit = onClickRow,
                    onSnooze1h = onSnooze1h,
                    onSnooze1d = onSnooze1d,
                    onUnsnooze = onUnsnooze,
                    onDelete = onDelete
                )
            }
            // Optional supporting line: distance / live price / notes
            val supporting = buildSupportingLine(row, isSnoozed)
            if (supporting != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!a.notes.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "\u270e ${a.notes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusPill(row: AlertRow, isSnoozed: Boolean) {
    val a = row.entity
    val (label, color) = when {
        isSnoozed -> "SNOOZED" to SnoozedColor
        !a.enabled -> "PAUSED" to SnoozedColor
        a.lastTriggeredAtMillis != null -> "FIRED" to PositiveGreen
        else -> "ARMED" to MaterialTheme.colorScheme.primary
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Composable
private fun AlertOverflow(
    isSnoozed: Boolean,
    onEdit: () -> Unit,
    onSnooze1h: () -> Unit,
    onSnooze1d: () -> Unit,
    onUnsnooze: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit() }
            )
            if (isSnoozed) {
                DropdownMenuItem(
                    text = { Text("Wake up") },
                    leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                    onClick = { expanded = false; onUnsnooze() }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Snooze 1 hour") },
                    leadingIcon = { Icon(Icons.Default.PauseCircle, contentDescription = null) },
                    onClick = { expanded = false; onSnooze1h() }
                )
                DropdownMenuItem(
                    text = { Text("Snooze until tomorrow") },
                    leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
                    onClick = { expanded = false; onSnooze1d() }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                onClick = { expanded = false; onDelete() }
            )
        }
    }
}

private fun buildSupportingLine(row: AlertRow, isSnoozed: Boolean): String? {
    val a = row.entity
    if (isSnoozed && a.snoozedUntilMillis != null) {
        val until = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(a.snoozedUntilMillis),
            ZoneId.of("Africa/Nairobi")
        )
        val hh = until.hour.toString().padStart(2, '0')
        val mm = until.minute.toString().padStart(2, '0')
        return "Resumes at $hh:$mm EAT"
    }
    val parts = mutableListOf<String>()
    row.livePrice?.let { p -> parts += "Last: %.2f".format(p) }
    val dist = row.distancePct
    if (dist != null) {
        val abs = kotlin.math.abs(dist)
        val direction = if (dist >= 0) "to go up" else "to go down"
        parts += "%.1f%% %s".format(abs, direction)
    }
    if (a.lastTriggeredAtMillis != null) {
        parts += "Last fired ${formatRelative(a.lastTriggeredAtMillis)}"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" \u00b7 ")
}

// ════════════════════════════════════════════════════════════════════════
// History tab
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun HistoryTab(
    events: List<AlertEventEntity>,
    onOpenSymbol: (String) -> Unit
) {
    if (events.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
            Text("No alerts have fired yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "When an alert triggers we'll record it here so you can find " +
                    "out what happened even if you missed the notification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val grouped = events.groupBy { dayBucket(it.firedAtMillis) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        grouped.forEach { (label, items) ->
            item(key = "h-$label") {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
                )
            }
            items(items, key = { "e-${it.id}" }) { e ->
                EventRow(event = e, onOpenSymbol = { onOpenSymbol(e.symbol) })
            }
        }
    }
}

@Composable
private fun EventRow(event: AlertEventEntity, onOpenSymbol: () -> Unit) {
    val type = runCatching { AlertType.valueOf(event.type) }.getOrNull()
    val (icon, accent) = if (type != null) iconAndColorFor(type)
        else (Icons.Default.ShowChart to MaterialTheme.colorScheme.primary)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSymbol),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.symbol,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                formatTimeOnly(event.firedAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Permission banner & empty state
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun PermissionBanner(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.NotificationsOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Notifications are blocked",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "You won't get alerts until permission is granted.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onGrant) { Text("Grant") }
        TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
}

@Composable
private fun EmptyAlertsState(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Bolt,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No alerts yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Get pinged on price moves, earnings, 52-week breakouts, " +
                "MA200 crosses and more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Create your first alert")
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Helpers
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun iconAndColorFor(type: AlertType): Pair<ImageVector, Color> = when (type) {
    AlertType.PRICE_ABOVE -> Icons.Default.TrendingUp to PositiveGreen
    AlertType.PRICE_BELOW -> Icons.Default.TrendingDown to NegativeRed
    AlertType.PERCENT_CHANGE_DAY -> Icons.Default.ShowChart to MaterialTheme.colorScheme.primary
    AlertType.PERCENT_ABOVE_ENTRY -> Icons.Default.TrendingUp to PositiveGreen
    AlertType.PERCENT_BELOW_ENTRY -> Icons.Default.TrendingDown to NegativeRed
    AlertType.EARNINGS_REMINDER -> Icons.Default.CalendarMonth to EarningsColor
    AlertType.FIFTY_TWO_WEEK_HIGH -> Icons.Default.TrendingUp to PositiveGreen
    AlertType.FIFTY_TWO_WEEK_LOW -> Icons.Default.TrendingDown to NegativeRed
    AlertType.MA200_CROSS_UP -> Icons.Default.TrendingUp to Color(0xFF009688)
    AlertType.MA200_CROSS_DOWN -> Icons.Default.TrendingDown to Color(0xFFFF7043)
    AlertType.VOLUME_SPIKE -> Icons.Default.Bolt to Color(0xFFFFC107)
    AlertType.ANALYST_TARGET_REACH -> Icons.Default.ShowChart to Color(0xFF00897B)
}

private fun dayBucket(epochMillis: Long): String {
    val now = ZonedDateTime.now(MarketClock.KENYA)
    val then = ZonedDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMillis),
        MarketClock.KENYA
    )
    val nowDate = now.toLocalDate()
    val thenDate = then.toLocalDate()
    return when {
        thenDate == nowDate -> "Today"
        thenDate == nowDate.minusDays(1) -> "Yesterday"
        else -> then.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}

private fun formatTimeOnly(epochMillis: Long): String {
    val dt = ZonedDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMillis),
        MarketClock.KENYA
    )
    return "%02d:%02d".format(dt.hour, dt.minute)
}

private fun formatRelative(epochMillis: Long): String {
    val diff = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> formatTimeOnly(epochMillis)
    }
}
