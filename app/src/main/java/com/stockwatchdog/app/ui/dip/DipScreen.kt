package com.stockwatchdog.app.ui.dip

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.components.changeColor
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.components.formatSignedPercent
import com.stockwatchdog.app.ui.diptracker.DipRow
import com.stockwatchdog.app.ui.diptracker.DipTrackerViewModel
import com.stockwatchdog.app.ui.diptracker.ZoneStatus
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen
import com.stockwatchdog.app.util.MarketClock
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime

// ── Status palette ──────────────────────────────────────────────────────
private val StrongBuyColor = Color(0xFF2E7D32)
private val InBuyZoneColor = PositiveGreen
private val NearZoneColor = Color(0xFFFFA000)
private val AboveZoneColor = Color(0xFF78909C)
private val BelowZoneColor = NegativeRed
private val EarningsColor = Color(0xFF6C5CE7)
private val MarketOpenColor = Color(0xFF2E7D32)
private val MarketClosedColor = Color(0xFF8E8E93)

@Composable
private fun statusColor(status: ZoneStatus): Color = when (status) {
    ZoneStatus.STRONG_BUY -> StrongBuyColor
    ZoneStatus.IN_BUY_ZONE -> InBuyZoneColor
    ZoneStatus.NEAR_ZONE -> NearZoneColor
    ZoneStatus.ABOVE_ZONE -> AboveZoneColor
    ZoneStatus.BELOW_ZONE -> BelowZoneColor
    ZoneStatus.NO_DATA -> MaterialTheme.colorScheme.outline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DipScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val vm: DipTrackerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DipTrackerViewModel(
                    dao = container.database.dipTrackerDao(),
                    repo = container.marketDataRepository,
                    detailsRepo = container.stockDetailsRepository
                )
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Live "now" tick that re-renders countdowns once per minute.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(state.undoDeleteEntity) {
        val entity = state.undoDeleteEntity ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${entity.symbol} removed",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            vm.undoDelete()
        } else {
            vm.dismissUndoSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dip", fontWeight = FontWeight.SemiBold)
                        if (state.rows.isNotEmpty()) {
                            Text(
                                "${state.rows.size} stock${if (state.rows.size == 1) "" else "s"} tracked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.openAddDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add stock") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp, top = 12.dp, bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MarketHoursCard(nowMs = nowMs) }

            if (state.rows.isNotEmpty()) {
                item { TrackerSummaryCard(state.rows) }
            }

            if (state.rows.isEmpty() && !state.isRefreshing) {
                item { EmptyDipState(onAdd = { vm.openAddDialog() }) }
            } else if (state.rows.isEmpty() && state.isRefreshing) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }

            items(state.rows, key = { it.entity.id }) { row ->
                DipRowCard(
                    row = row,
                    nowMs = nowMs,
                    onClick = { onOpenSymbol(row.entity.symbol) },
                    onEdit = { vm.openEditDialog(row.entity.id) },
                    onDelete = { vm.confirmDelete(row.entity.id) }
                )
            }
        }
    }

    // ── Add/Edit dialog ────────────────────────────────────────────────
    if (state.dialogOpen) {
        DipAddEditDialog(
            isEditing = state.dialogEditingId != null,
            symbol = state.symbolDraft,
            buyZoneLow = state.buyZoneLowDraft,
            buyZoneHigh = state.buyZoneHighDraft,
            strongBuy = state.strongBuyDraft,
            notes = state.notesDraft,
            searchQuery = state.searchQuery,
            searchResults = state.searchResults,
            isSearching = state.isSearching,
            selectedSymbol = state.selectedSymbol,
            selectedQuote = state.selectedQuote,
            onSearchQueryChange = vm::onSearchQueryChange,
            onSelectSymbol = vm::selectSymbol,
            onClearSymbol = vm::clearSelectedSymbol,
            onSymbolChange = vm::onSymbolChange,
            onBuyZoneLowChange = vm::onBuyZoneLowChange,
            onBuyZoneHighChange = vm::onBuyZoneHighChange,
            onStrongBuyChange = vm::onStrongBuyChange,
            onNotesChange = vm::onNotesChange,
            onApplyPresetBuyZone = vm::applyPresetBuyZone,
            onApplyPresetStrongBuy = vm::applyPresetStrongBuy,
            onSave = vm::save,
            onDismiss = vm::closeDialog
        )
    }

    state.confirmDeleteId?.let { id ->
        val entity = state.rows.firstOrNull { it.entity.id == id }?.entity
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text("Stop tracking?") },
            text = {
                Text(
                    if (entity != null) "Remove ${entity.symbol} from your dip list?"
                    else "Remove this setup?"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(id) }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDelete() }) { Text("Cancel") }
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════════
// Market Hours hero card
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun MarketHoursCard(nowMs: Long) {
    val nyse = MarketClock.status(MarketClock.Market.US_NYSE, nowMs)
    val lse = MarketClock.status(MarketClock.Market.UK_LSE, nowMs)
    val nairobi = ZonedDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(nowMs),
        ZoneId.of("Africa/Nairobi")
    )
    val timeStr = "%02d:%02d".format(nairobi.hour, nairobi.minute)
    val day = nairobi.dayOfWeek.name.take(3).lowercase()
        .replaceFirstChar { it.uppercaseChar() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Nairobi · $day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$timeStr EAT",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            MarketHourRow(status = nyse)
            MarketHourRow(status = lse)
        }
    }
}

@Composable
private fun MarketHourRow(status: MarketClock.MarketStatus) {
    val color = if (status.isOpen) MarketOpenColor else MarketClosedColor
    val verb = if (status.isOpen) "closes in" else "opens in"
    val countdown = MarketClock.formatCountdown(status.until)
    val nextLabel = MarketClock.formatKenyaShort(status.nextChangeAtKenya)

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.market.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status.isOpen) "OPEN" else "CLOSED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier
                        .background(color.copy(alpha = 0.13f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Text(
                "$verb $countdown · $nextLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Tracker summary
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun TrackerSummaryCard(rows: List<DipRow>) {
    val ready = rows.count { it.status == ZoneStatus.STRONG_BUY || it.status == ZoneStatus.IN_BUY_ZONE }
    val near = rows.count { it.status == ZoneStatus.NEAR_ZONE }
    val total = rows.size
    val nextEarnings = rows
        .mapNotNull { it.details?.nextEarningsEpochSeconds?.let { e -> it to e } }
        .filter { it.second * 1000L > System.currentTimeMillis() }
        .minByOrNull { it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPill("$ready", "Ready", InBuyZoneColor, Modifier.weight(1f))
                SummaryPill("$near", "Near", NearZoneColor, Modifier.weight(1f))
                SummaryPill("$total", "Tracked", AboveZoneColor, Modifier.weight(1f))
            }
            if (nextEarnings != null) {
                val (row, eps) = nextEarnings
                val days = MarketClock.daysUntil(eps).coerceAtLeast(0)
                val whenStr = MarketClock.formatKenyaLong(eps)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            EarningsColor.copy(alpha = 0.10f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = EarningsColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Next earnings: ${row.entity.symbol}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = EarningsColor
                        )
                        Text(
                            "$whenStr · in $days day${if (days == 1L) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ════════════════════════════════════════════════════════════════════════
// Per-stock card
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun DipRowCard(
    row: DipRow,
    nowMs: Long,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusCol by animateColorAsState(
        targetValue = statusColor(row.status),
        label = "statusColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header: symbol, name, status, price ──────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(statusCol)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.entity.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        if (row.status != ZoneStatus.NO_DATA) {
                            StatusBadge(row.status, statusCol)
                        }
                    }
                    val sub = row.name ?: row.details?.symbol
                    if (!sub.isNullOrBlank() && sub != row.entity.symbol) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    modifier = Modifier.widthIn(min = 84.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        formatPrice(row.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    val pct = row.percentChange
                    if (pct != null) {
                        Text(
                            formatSignedPercent(pct),
                            style = MaterialTheme.typography.labelSmall,
                            color = changeColor(pct),
                            maxLines = 1
                        )
                    }
                }
                RowOverflow(onEdit = onEdit, onDelete = onDelete)
            }

            // ── 52-week range visualisation ──────────────────────────
            if (row.details != null) {
                FiftyTwoWeekRangeBar(
                    details = row.details,
                    currentPrice = row.currentPrice,
                    statusColor = statusCol
                )
            }

            // ── Buy zone bar (existing pattern, simplified) ──────────
            BuyZoneBar(row = row, statusColor = statusCol)

            // ── Metric chips grid (2 rows of 2) ──────────────────────
            MetricsGrid(row = row, nowMs = nowMs)

            // ── Notes ───────────────────────────────────────────────
            row.entity.notes?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RowOverflow(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
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
            DropdownMenuItem(
                text = { Text("Remove") },
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

@Composable
private fun StatusBadge(status: ZoneStatus, color: Color) {
    if (status == ZoneStatus.NO_DATA) return
    Text(
        status.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
