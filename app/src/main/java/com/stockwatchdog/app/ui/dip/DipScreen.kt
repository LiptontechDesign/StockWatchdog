package com.stockwatchdog.app.ui.dip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ── Status palette ──────────────────────────────────────────────────────
private val StrongBuyColor = Color(0xFF2E7D32)
private val InBuyZoneColor = PositiveGreen
private val NearZoneColor = Color(0xFFFFA000)
private val AboveZoneColor = Color(0xFF78909C)
private val BelowZoneColor = NegativeRed
private val EarningsColor = Color(0xFF6C5CE7)
private val MarketOpenColor = Color(0xFF2E7D32)
private val MarketClosedColor = Color(0xFF8E8E93)
private val RefPageBg = Color(0xFFF0EFF7)
private val RefTextDark = Color(0xFF0D0B1E)
private val RefNavy = Color(0xFF1E1852)
private val RefNavyMid = Color(0xFF3D3894)
private val RefBorder = Color(0xFFC8C5E8)
private val RefChipBorder = Color(0xFFDDDAF5)
private val RefSuccessBg = Color(0xFFC6EFD8)
private val RefSuccessText = Color(0xFF0A4A28)
private val RefNearBg = Color(0xFFFDE8C0)
private val RefWarningText = Color(0xFF7A3D00)
private val RefTrackBg = Color(0xFFDDDAF5)
private val NairobiZone: ZoneId = ZoneId.of("Africa/Nairobi")
private val ReleaseDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

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
    onOpenSymbol: (String) -> Unit,
    onOpenFinancials: (String) -> Unit
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
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 13.dp, end = 13.dp, top = 14.dp, bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                DipHeader(
                    trackedCount = state.rows.size,
                    isRefreshing = state.isRefreshing,
                    onAdd = { vm.openAddDialog() },
                    onRefresh = { vm.refresh() }
                )
            }
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
                    onOpenFinancials = { onOpenFinancials(row.entity.symbol) },
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
private fun DipHeader(
    trackedCount: Int,
    isRefreshing: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Dip",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "$trackedCount stock${if (trackedCount == 1) "" else "s"} tracked",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RefIconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            RefIconButton(onClick = onRefresh) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun RefIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(9.dp))
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val readyFill = if (isDark) PositiveGreen.copy(alpha = 0.14f) else RefSuccessBg
    val readyText = if (isDark) Color(0xFF86EFAC) else RefSuccessText
    val nearFill = if (isDark) NearZoneColor.copy(alpha = 0.16f) else RefNearBg
    val nearText = if (isDark) Color(0xFFFACC15) else RefWarningText
    val trackedFill = if (isDark) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f) else RefTrackBg
    val trackedText = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else RefNavy

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        SummaryPill("$ready", "Ready", readyFill, readyText, Modifier.weight(1f))
        SummaryPill("$near", "Near", nearFill, nearText, Modifier.weight(1f))
        SummaryPill("$total", "Tracked", trackedFill, trackedText, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryPill(
    value: String,
    label: String,
    fill: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(64.dp),
        color = fill,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.20f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = textColor
            )
        }
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
    onOpenFinancials: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var detailsExpanded by rememberSaveable(row.entity.symbol) { mutableStateOf(false) }
    val statusCol by animateColorAsState(
        targetValue = statusColor(row.status),
        label = "statusColor"
    )
    val badgeColors = refBadgeColors(row.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 13.dp, end = 8.dp, top = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusCol)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.entity.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        if (row.status != ZoneStatus.NO_DATA) {
                            RefStatusBadge(row.status, badgeColors.first, badgeColors.second)
                        }
                    }
                    val sub = row.name ?: row.details?.symbol
                    if (!sub.isNullOrBlank() && sub != row.entity.symbol) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    modifier = Modifier.widthIn(min = 76.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        formatPrice(row.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    row.percentChange?.let { pct ->
                        Text(
                            formatSignedPercent(pct),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = changeColor(pct),
                            maxLines = 1
                        )
                    }
                }
                RowOverflow(onEdit = onEdit, onDelete = onDelete)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DipInfoChip("Earnings", compactEarningsChip(row.details, nowMs), Modifier.weight(1f))
                DipInfoChip("Buy zone", compactBuyZone(row), Modifier.weight(1f))
                DipInfoChip(
                    "Need drop",
                    needDropText(row),
                    Modifier.weight(1f),
                    warning = row.currentPrice?.let { it > row.entity.buyZoneHigh } == true
                )
            }

            MoreDetailToggle(
                expanded = detailsExpanded,
                onToggle = { detailsExpanded = !detailsExpanded }
            )

            AnimatedVisibility(visible = detailsExpanded) {
                DipExpandedDetails(
                    row = row,
                    nowMs = nowMs,
                    onOpenSymbol = onOpenFinancials
                )
            }
        }
    }
}

@Composable
private fun MoreDetailToggle(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(end = 5.dp)
            .clickable(onClick = onToggle),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (expanded) "LESS DETAIL" else "MORE DETAIL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DipExpandedDetails(
    row: DipRow,
    nowMs: Long,
    onOpenSymbol: () -> Unit
) {
    val lines = buildDipDetailLines(row, nowMs)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 5.dp, bottom = 12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        lines.forEach { line ->
            DipDetailLine(line)
        }
        TextButton(
            onClick = onOpenSymbol,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = 44.dp)
        ) {
            Text(
                "OPEN FULL FINANCIALS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DipDetailLine(line: DipDetailText) {
    val color = when (line.tone) {
        DipDetailTone.GOOD -> RefSuccessText
        DipDetailTone.WARNING -> RefWarningText
        DipDetailTone.BAD -> Color(0xFF7A1A1A)
        DipDetailTone.NEUTRAL -> RefNavy
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                line.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                line.text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private data class DipDetailText(
    val label: String,
    val text: String,
    val tone: DipDetailTone
)

private enum class DipDetailTone { GOOD, WARNING, BAD, NEUTRAL }

private fun buildDipDetailLines(row: DipRow, nowMs: Long): List<DipDetailText> = buildList {
    add(
        DipDetailText(
            label = "Status",
            text = statusPlainText(row),
            tone = when (row.status) {
                ZoneStatus.STRONG_BUY,
                ZoneStatus.IN_BUY_ZONE -> DipDetailTone.GOOD
                ZoneStatus.NEAR_ZONE,
                ZoneStatus.ABOVE_ZONE -> DipDetailTone.WARNING
                ZoneStatus.BELOW_ZONE -> DipDetailTone.BAD
                ZoneStatus.NO_DATA -> DipDetailTone.NEUTRAL
            }
        )
    )
    add(
        DipDetailText(
            label = "Buy plan",
            text = buyPlanPlainText(row),
            tone = DipDetailTone.NEUTRAL
        )
    )
    add(
        DipDetailText(
            label = "Move needed",
            text = moveNeededPlainText(row),
            tone = when {
                row.currentPrice == null -> DipDetailTone.NEUTRAL
                row.currentPrice in row.entity.buyZoneLow..row.entity.buyZoneHigh -> DipDetailTone.GOOD
                row.currentPrice > row.entity.buyZoneHigh -> DipDetailTone.WARNING
                else -> DipDetailTone.BAD
            }
        )
    )
    earningsPlainText(row.details, nowMs)?.let {
        add(DipDetailText("Results", it, DipDetailTone.WARNING))
    }
    analystPlainText(row)?.let {
        add(DipDetailText("Analyst view", it, analystTone(row.details)))
    }
    trendPlainText(row)?.let {
        add(DipDetailText("Trend", it, DipDetailTone.NEUTRAL))
    }
    row.entity.notes?.takeIf { it.isNotBlank() }?.let {
        add(DipDetailText("Your note", it, DipDetailTone.NEUTRAL))
    }
}

private fun statusPlainText(row: DipRow): String = when (row.status) {
    ZoneStatus.STRONG_BUY ->
        "Price is at your strongest buy level. This is the deeper discount level you chose."
    ZoneStatus.IN_BUY_ZONE ->
        "Price is inside your planned buy range, so this ticker is ready to review."
    ZoneStatus.NEAR_ZONE ->
        "Price is close to your buy range. Keep watching; it is not quite there yet."
    ZoneStatus.ABOVE_ZONE ->
        "Price is still above your buy range. Wait for the drop you planned before buying."
    ZoneStatus.BELOW_ZONE ->
        "Price is already below your zone. Check news and financials before buying because the fall may have a reason."
    ZoneStatus.NO_DATA ->
        "No fresh price is available yet, so the app cannot judge the buy zone."
}

private fun buyPlanPlainText(row: DipRow): String {
    val zone = "${formatCompactDollar(row.entity.buyZoneLow)} to ${formatCompactDollar(row.entity.buyZoneHigh)}"
    val strong = row.entity.strongBuyBelow?.let { " Strong buy below ${formatCompactDollar(it)}." }.orEmpty()
    return "Your normal buy zone is $zone.$strong"
}

private fun moveNeededPlainText(row: DipRow): String {
    val current = row.currentPrice ?: return "Waiting for a fresh price before calculating the needed move."
    return when {
        current in row.entity.buyZoneLow..row.entity.buyZoneHigh ->
            "Already inside your buy zone."
        current > row.entity.buyZoneHigh -> {
            val pct = (row.entity.buyZoneHigh / current - 1.0) * 100.0
            "Needs about ${"%.1f".format(kotlin.math.abs(pct))}% drop to enter your buy zone."
        }
        else ->
            "Already below your buy zone. Treat this as a caution signal and check why it dropped."
    }
}

private fun earningsPlainText(details: StockDetails?, nowMs: Long): String? {
    val epoch = details?.nextEarningsEpochSeconds ?: return null
    val releaseDate = Instant.ofEpochSecond(epoch).atZone(NairobiZone).toLocalDate()
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(NairobiZone).toLocalDate()
    val days = ChronoUnit.DAYS.between(nowDate, releaseDate)
    val dateText = releaseDate.format(ReleaseDateFormat)
    val quarter = details.nextEarningsQuarterLabel
        ?.takeIf { it.isNotBlank() }
        ?.let { " (${compactQuarterLabel(it)})" }
        .orEmpty()
    val whenText = when {
        days < 0 -> "reported recently"
        days == 0L -> "reports today"
        days == 1L -> "reports tomorrow"
        else -> "reports in ${days}d"
    }
    return "Next results $dateText$quarter, $whenText. Fresh numbers can change the buy case."
}

private fun analystPlainText(row: DipRow): String? {
    val details = row.details ?: return null
    val pieces = buildList {
        details.analystRecommendation
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.replace("_", " ")
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ?.let { add(it) }
        details.analystTargetMean?.let { target ->
            val upside = details.upsideToTargetPct(row.currentPrice)
                ?.let { " (${formatSignedPercent(it)} from now)" }
                .orEmpty()
            add("target ${formatPrice(target)}$upside")
        }
    }
    return pieces.takeIf { it.isNotEmpty() }?.joinToString(". ")
}

private fun analystTone(details: StockDetails?): DipDetailTone = when (
    details?.analystRecommendation?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_")
) {
    "strong_buy", "buy" -> DipDetailTone.GOOD
    "sell", "strong_sell" -> DipDetailTone.BAD
    "hold" -> DipDetailTone.WARNING
    else -> DipDetailTone.NEUTRAL
}

private fun trendPlainText(row: DipRow): String? {
    val details = row.details ?: return null
    val trend = details.pctVs200dMa(row.currentPrice)?.let { pct ->
        val side = if (pct >= 0) "above" else "below"
        "Price is ${formatPercentOne(kotlin.math.abs(pct))} $side the 200-day average."
    }
    val range = details.positionInRange(row.currentPrice)?.let { position ->
        "It sits near ${"%.0f".format(position * 100f)}% of its 52-week range."
    }
    val volume = details.volumeSpikeRatio()?.takeIf { it >= 1.3 }?.let {
        "Volume is ${"%.1f".format(it)}x usual, so the move has extra attention."
    }
    return listOfNotNull(trend, range, volume).takeIf { it.isNotEmpty() }?.joinToString(" ")
}

@Composable
private fun RefStatusBadge(status: ZoneStatus, fill: Color, text: Color) {
    Text(
        status.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = text,
        modifier = Modifier
            .background(fill, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1
    )
}

@Composable
private fun DipInfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false
) {
    val fill = if (warning) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val labelColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .background(fill, RoundedCornerShape(7.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun refBadgeColors(status: ZoneStatus): Pair<Color, Color> = when (status) {
    ZoneStatus.NEAR_ZONE -> Color(0xFFFFF0C0) to Color(0xFF5A3000)
    ZoneStatus.IN_BUY_ZONE,
    ZoneStatus.STRONG_BUY -> RefSuccessBg to RefSuccessText
    ZoneStatus.ABOVE_ZONE -> RefNearBg to RefWarningText
    ZoneStatus.BELOW_ZONE -> Color(0xFFFCE4E4) to Color(0xFF7A1A1A)
    ZoneStatus.NO_DATA -> RefTrackBg to RefNavy
}

private fun compactEarningsChip(details: StockDetails?, nowMs: Long): String {
    val epoch = details?.nextEarningsEpochSeconds ?: return "--"
    val date = Instant.ofEpochSecond(epoch).atZone(NairobiZone).toLocalDate()
    val now = Instant.ofEpochMilli(nowMs).atZone(NairobiZone).toLocalDate()
    val days = ChronoUnit.DAYS.between(now, date)
    return if (days in 0..9) "${days}d" else date.format(ReleaseDateFormat)
}

private fun compactBuyZone(row: DipRow): String =
    "${formatCompactDollar(row.entity.buyZoneLow)}-${formatCompactDollar(row.entity.buyZoneHigh)}"

private fun needDropText(row: DipRow): String {
    val current = row.currentPrice ?: return "--"
    return when {
        current in row.entity.buyZoneLow..row.entity.buyZoneHigh -> "Ready"
        current > row.entity.buyZoneHigh -> {
            val pct = (row.entity.buyZoneHigh / current - 1.0) * 100.0
            "${"%.0f".format(pct)}%"
        }
        else -> "Below"
    }
}

private fun formatCompactDollar(value: Double): String =
    if (value >= 10) "\$${"%.0f".format(value)}" else "\$${"%.2f".format(value)}"

private fun formatPercentOne(value: Double): String = "%.1f%%".format(value)

@Composable
private fun OldDipRowCard(
    row: DipRow,
    nowMs: Long,
    onClick: () -> Unit,
    onOpenFinancials: () -> Unit = onClick,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusCol by animateColorAsState(
        targetValue = statusColor(row.status),
        label = "statusColor"
    )
    val badgeColors = refBadgeColors(row.status)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(2.dp, RefBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.padding(start = 13.dp, end = 8.dp, top = 11.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Header: symbol, name, status, price ──────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusCol)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.entity.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = RefTextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        if (row.status != ZoneStatus.NO_DATA) {
                            RefStatusBadge(row.status, badgeColors.first, badgeColors.second)
                        }
                    }
                    val sub = row.name ?: row.details?.symbol
                    if (!sub.isNullOrBlank() && sub != row.entity.symbol) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RefNavyMid,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    modifier = Modifier.widthIn(min = 76.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        formatPrice(row.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = RefTextDark,
                        maxLines = 1
                    )
                    val pct = row.percentChange
                    if (pct != null) {
                        Text(
                            formatSignedPercent(pct),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = changeColor(pct),
                            maxLines = 1
                        )
                    }
                }
                RowOverflow(onEdit = onEdit, onDelete = onDelete)
            }

            // ── 52-week range visualisation ──────────────────────────
            DipExpandedDetails(row = row, nowMs = nowMs, onOpenSymbol = onOpenFinancials)

            // ── Buy zone bar (existing pattern, simplified) ──────────
            Spacer(Modifier.height(0.dp))

            // ── Metric chips grid (2 rows of 2) ──────────────────────
            Spacer(Modifier.height(0.dp))

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
private fun DipReleaseDateLine(details: StockDetails?, nowMs: Long) {
    val text = dipReleaseDateText(details, nowMs) ?: return
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = EarningsColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun dipReleaseDateText(details: StockDetails?, nowMs: Long): String? {
    details ?: return null
    val epoch = details.nextEarningsEpochSeconds ?: return null
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(NairobiZone).toLocalDate()
    val releaseDate = Instant.ofEpochSecond(epoch).atZone(NairobiZone).toLocalDate()
    val days = ChronoUnit.DAYS.between(nowDate, releaseDate)
    if (days < -3) return null

    val dateText = (if (details.nextEarningsIsEstimate == true) "~" else "") +
        releaseDate.format(ReleaseDateFormat)
    val period = details.nextEarningsQuarterLabel
        ?.takeIf { it.isNotBlank() }
        ?.let(::compactQuarterLabel)

    return when {
        days == 0L -> listOfNotNull(period, "results today").joinToString(" ")
        days == 1L -> listOfNotNull(period, "results tomorrow").joinToString(" ")
        days in 2..14 -> listOfNotNull(period, "results $dateText (${days}d)").joinToString(" ")
        else -> listOfNotNull(period, "results $dateText").joinToString(" ")
    }.replaceFirstChar { it.uppercaseChar() }
}

private fun compactQuarterLabel(label: String): String {
    val match = Regex("""Q([1-4])\s+(\d{4})""").matchEntire(label.trim().uppercase())
    return if (match != null) {
        val q = match.groupValues[1]
        val year = match.groupValues[2].takeLast(2)
        "Q$q'$year"
    } else {
        label
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
