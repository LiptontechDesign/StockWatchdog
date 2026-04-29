package com.stockwatchdog.app.ui.diptracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.domain.SymbolMatch
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen

private val StrongBuyColor = Color(0xFF2E7D32)
private val InBuyZoneColor = PositiveGreen
private val NearZoneColor = Color(0xFFFFA000)
private val AboveZoneColor = Color(0xFF78909C)
private val BelowZoneColor = NegativeRed

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
fun DipTrackerScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val vm: DipTrackerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DipTrackerViewModel(
                    dao = container.database.dipTrackerDao(),
                    repo = container.marketDataRepository
                )
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Dip Tracker", fontWeight = FontWeight.SemiBold)
                            if (state.rows.isNotEmpty()) {
                                Text(
                                    "${state.rows.size} tracking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
            FloatingActionButton(onClick = { vm.openAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Add setup")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.rows.isEmpty() && state.isRefreshing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.rows.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No setups yet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to add a stock or ETF you want to buy on a dip. " +
                            "Set your buy zone and get a live status showing when it's time to act.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.rows.isNotEmpty()) {
                    item { SummaryCard(state.rows) }
                }
                items(state.rows, key = { it.entity.id }) { row ->
                    DipRowCardItem(
                        row = row,
                        onClick = { onOpenSymbol(row.entity.symbol) },
                        onEdit = { vm.openEditDialog(row.entity.id) },
                        onDelete = { vm.confirmDelete(row.entity.id) }
                    )
                }
            }
        }
    }

    // ── Add/Edit dialog ────────────────────────────────────────────────
    if (state.dialogOpen) {
        EditDipSheetDialog(
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

    // ── Confirm-delete dialog ──────────────────────────────────────────
    state.confirmDeleteId?.let { id ->
        val entity = state.rows.firstOrNull { it.entity.id == id }?.entity
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text("Remove setup?") },
            text = {
                Text(
                    if (entity != null) "Remove ${entity.symbol} from your dip tracker?"
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

@Composable
private fun SummaryCard(rows: List<DipRow>) {
    val inZone = rows.count { it.status == ZoneStatus.IN_BUY_ZONE || it.status == ZoneStatus.STRONG_BUY }
    val near = rows.count { it.status == ZoneStatus.NEAR_ZONE }
    val total = rows.size
    val bestSetup = rows.firstOrNull { it.status != ZoneStatus.NO_DATA }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Buy opportunities",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        bestSetup?.let {
                            "${it.entity.symbol} is ${distanceLabel(it) ?: it.status.label.lowercase()}"
                        } ?: "Waiting for quote data",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                bestSetup?.let {
                    StatusBadge(it.status, statusColor(it.status))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryMetric(
                    count = inZone,
                    label = "Ready",
                    color = InBuyZoneColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    count = near,
                    label = "Near",
                    color = NearZoneColor,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetric(
                    count = total,
                    label = "Tracked",
                    color = AboveZoneColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DipRowCardItem(
    row: DipRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusCol by animateColorAsState(
        targetValue = statusColor(row.status),
        label = "statusColor"
    )
    val distanceText = distanceLabel(row)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                        StatusBadge(row.status, statusCol)
                    }
                    row.name?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    modifier = Modifier.widthIn(min = 82.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        formatPrice(row.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    distanceText?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusTextColor(row.status),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                RowActions(onEdit = onEdit, onDelete = onDelete)
            }

            PriceZoneRail(row = row, statusColor = statusCol)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZoneValue(
                    label = "Buy zone",
                    value = "${formatPrice(row.entity.buyZoneLow)} - ${formatPrice(row.entity.buyZoneHigh)}",
                    color = InBuyZoneColor,
                    modifier = Modifier.weight(1f)
                )
                row.entity.strongBuyBelow?.let { strongBuy ->
                    ZoneValue(
                        label = "Strong buy",
                        value = formatPrice(strongBuy),
                        color = StrongBuyColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            row.entity.notes?.let { note ->
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
private fun RowActions(
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                },
                onClick = {
                    expanded = false
                    onEdit()
                }
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
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun PriceZoneRail(row: DipRow, statusColor: Color) {
    val low = row.entity.buyZoneLow
    val high = row.entity.buyZoneHigh
    val current = row.currentPrice
    val strongBuy = row.entity.strongBuyBelow
    val rawMin = minOf(low, strongBuy ?: low, current ?: low)
    val rawMax = maxOf(high, current ?: high)
    val span = (rawMax - rawMin).coerceAtLeast((high - low).coerceAtLeast(high * 0.04))
    val minValue = (rawMin - span * 0.08).coerceAtLeast(0.0)
    val maxValue = rawMax + span * 0.08
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val zoneColor = InBuyZoneColor.copy(alpha = 0.72f)
    val strongColor = StrongBuyColor.copy(alpha = 0.78f)
    val markerRing = MaterialTheme.colorScheme.surface

    fun fraction(value: Double): Float =
        ((value - minValue) / (maxValue - minValue)).toFloat().coerceIn(0f, 1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
    ) {
        val centerY = size.height / 2f
        val barHeight = 6.dp.toPx()
        val radius = barHeight / 2f
        val top = centerY - barHeight / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(radius, radius)
        )

        strongBuy?.let {
            val end = fraction(it) * size.width
            drawRoundRect(
                color = strongColor,
                topLeft = Offset(0f, top),
                size = Size(end.coerceAtLeast(3.dp.toPx()), barHeight),
                cornerRadius = CornerRadius(radius, radius)
            )
        }

        val zoneStart = fraction(low) * size.width
        val zoneEnd = fraction(high) * size.width
        drawRoundRect(
            color = zoneColor,
            topLeft = Offset(zoneStart, top),
            size = Size((zoneEnd - zoneStart).coerceAtLeast(4.dp.toPx()), barHeight),
            cornerRadius = CornerRadius(radius, radius)
        )

        current?.let {
            val markerX = fraction(it) * size.width
            drawCircle(
                color = markerRing,
                radius = 6.dp.toPx(),
                center = Offset(markerX, centerY)
            )
            drawCircle(
                color = statusColor,
                radius = 4.dp.toPx(),
                center = Offset(markerX, centerY)
            )
        }
    }
}

@Composable
private fun ZoneValue(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun statusTextColor(status: ZoneStatus): Color = when (status) {
    ZoneStatus.STRONG_BUY -> StrongBuyColor
    ZoneStatus.IN_BUY_ZONE -> InBuyZoneColor
    ZoneStatus.NEAR_ZONE -> NearZoneColor
    ZoneStatus.BELOW_ZONE -> BelowZoneColor
    ZoneStatus.ABOVE_ZONE,
    ZoneStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun distanceLabel(row: DipRow): String? {
    val distance = row.distanceToBuyZonePct ?: return null
    return when (row.status) {
        ZoneStatus.STRONG_BUY -> "at strong buy"
        ZoneStatus.IN_BUY_ZONE -> "inside buy zone"
        ZoneStatus.NEAR_ZONE,
        ZoneStatus.ABOVE_ZONE -> "${"%.1f".format(distance)}% above buy zone"
        ZoneStatus.BELOW_ZONE -> "${"%.1f".format(distance)}% below buy zone"
        ZoneStatus.NO_DATA -> null
    }
}

@Composable
private fun DipRowItem(
    row: DipRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusCol by animateColorAsState(
        targetValue = statusColor(row.status),
        label = "statusColor"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusCol)
        )
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.entity.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(row.status, statusCol)
            }
            row.name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Buy zone: ${formatPrice(row.entity.buyZoneLow)} – ${formatPrice(row.entity.buyZoneHigh)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            row.entity.strongBuyBelow?.let { sb ->
                Text(
                    "Strong buy below: ${formatPrice(sb)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StrongBuyColor.copy(alpha = 0.8f)
                )
            }
            row.entity.notes?.let { n ->
                Text(
                    n,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (row.currentPrice != null) {
                Text(
                    formatPrice(row.currentPrice),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                row.distanceToBuyZonePct?.let { dist ->
                    val text = when (row.status) {
                        ZoneStatus.STRONG_BUY -> "At strong buy"
                        ZoneStatus.IN_BUY_ZONE -> "In zone"
                        ZoneStatus.NEAR_ZONE,
                        ZoneStatus.ABOVE_ZONE -> "${"%.2f".format(dist)}% above"
                        ZoneStatus.BELOW_ZONE -> "${"%.2f".format(dist)}% below"
                        ZoneStatus.NO_DATA -> "--"
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (row.status) {
                            ZoneStatus.STRONG_BUY -> StrongBuyColor
                            ZoneStatus.IN_BUY_ZONE -> InBuyZoneColor
                            ZoneStatus.NEAR_ZONE -> NearZoneColor
                            ZoneStatus.BELOW_ZONE -> BelowZoneColor
                            ZoneStatus.ABOVE_ZONE,
                            ZoneStatus.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            } else {
                Text(
                    "--",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
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
            .background(
                color = color.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun EditDipSheetDialog(
    isEditing: Boolean,
    symbol: String,
    buyZoneLow: String,
    buyZoneHigh: String,
    strongBuy: String,
    notes: String,
    searchQuery: String,
    searchResults: List<SymbolMatch>,
    isSearching: Boolean,
    selectedSymbol: SymbolMatch?,
    selectedQuote: Quote?,
    onSearchQueryChange: (String) -> Unit,
    onSelectSymbol: (SymbolMatch) -> Unit,
    onClearSymbol: () -> Unit,
    onSymbolChange: (String) -> Unit,
    onBuyZoneLowChange: (String) -> Unit,
    onBuyZoneHighChange: (String) -> Unit,
    onStrongBuyChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onApplyPresetBuyZone: (Double) -> Unit,
    onApplyPresetStrongBuy: (Double) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val low = buyZoneLow.toDoubleOrNull()
    val high = buyZoneHigh.toDoubleOrNull()
    val strong = strongBuy.toDoubleOrNull()
    val zoneError = low != null && high != null && low > high
    val strongError = strong != null && low != null && strong > low
    val activeSymbol = selectedSymbol?.symbol ?: symbol.trim().uppercase()
    val hasSymbol = activeSymbol.isNotBlank()
    val canSave = hasSymbol &&
        low != null &&
        high != null &&
        low > 0 &&
        high > 0 &&
        !zoneError &&
        !strongError
    val typedSymbol = searchQuery.trim().uppercase()
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (isEditing) "Edit buy zone" else "Add buy zone",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 540.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (hasSymbol) {
                            SelectedDipSymbolPanel(
                                symbol = activeSymbol,
                                name = selectedSymbol?.name,
                                currentPrice = selectedQuote?.price,
                                onClearSymbol = onClearSymbol
                            )
                        } else {
                            SymbolSearchPanel(
                                searchQuery = searchQuery,
                                typedSymbol = typedSymbol,
                                searchResults = searchResults,
                                isSearching = isSearching,
                                onSearchQueryChange = onSearchQueryChange,
                                onSelectSymbol = onSelectSymbol,
                                onUseTypedSymbol = {
                                    onSymbolChange(typedSymbol)
                                    onSearchQueryChange("")
                                }
                            )
                        }

                        if (hasSymbol) {
                            selectedQuote?.let {
                                QuickZonePresets(onApplyPresetBuyZone = onApplyPresetBuyZone)
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DialogSectionLabel("Target zone")
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = buyZoneLow,
                                        onValueChange = onBuyZoneLowChange,
                                        label = { Text("Low") },
                                        placeholder = { Text("212.00") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        isError = zoneError
                                    )
                                    OutlinedTextField(
                                        value = buyZoneHigh,
                                        onValueChange = onBuyZoneHighChange,
                                        label = { Text("High") },
                                        placeholder = { Text("216.00") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        isError = zoneError
                                    )
                                }
                                if (zoneError) {
                                    Text(
                                        "Low must be below high",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DialogSectionLabel("Buy trigger")
                                if (low != null && low > 0) {
                                    AssistChip(
                                        onClick = { onApplyPresetStrongBuy(10.0) },
                                        label = { Text("10% below zone low") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                OutlinedTextField(
                                    value = strongBuy,
                                    onValueChange = onStrongBuyChange,
                                    label = { Text("Strong buy below") },
                                    placeholder = { Text("Optional") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = strongError,
                                    supportingText = {
                                        if (strongError) {
                                            Text("Must be at or below zone low")
                                        }
                                    }
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DialogSectionLabel("Notes")
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = onNotesChange,
                                    label = { Text("Thesis") },
                                    placeholder = { Text("Optional") },
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onSave, enabled = canSave) {
                            Text(if (isEditing) "Save" else "Add")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolSearchPanel(
    searchQuery: String,
    typedSymbol: String,
    searchResults: List<SymbolMatch>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectSymbol: (SymbolMatch) -> Unit,
    onUseTypedSymbol: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DialogSectionLabel("Symbol")
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search") },
            placeholder = { Text("AAPL or Apple") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (isSearching) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        } else if (searchResults.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                searchResults.take(5).forEachIndexed { index, match ->
                    SearchResultRow(match = match, onSelectSymbol = onSelectSymbol)
                    if (index < searchResults.take(5).lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
        if (typedSymbol.isNotBlank()) {
            TextButton(onClick = onUseTypedSymbol) {
                Text("Use $typedSymbol")
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    match: SymbolMatch,
    onSelectSymbol: (SymbolMatch) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelectSymbol(match) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                match.symbol,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            match.name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SelectedDipSymbolPanel(
    symbol: String,
    name: String?,
    currentPrice: Double?,
    onClearSymbol: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            currentPrice?.let {
                Text(
                    "Current ${formatPrice(it)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onClearSymbol) {
            Icon(Icons.Default.Close, contentDescription = "Clear symbol")
        }
    }
}

@Composable
private fun QuickZonePresets(onApplyPresetBuyZone: (Double) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DialogSectionLabel("Quick zone")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(5.0, 10.0, 20.0, 30.0, 40.0).forEach { pct ->
                AssistChip(
                    onClick = { onApplyPresetBuyZone(pct) },
                    label = { Text("${pct.toInt()}%") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DialogSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EditDipDialog(
    isEditing: Boolean,
    symbol: String,
    buyZoneLow: String,
    buyZoneHigh: String,
    strongBuy: String,
    notes: String,
    searchQuery: String,
    searchResults: List<SymbolMatch>,
    isSearching: Boolean,
    selectedSymbol: SymbolMatch?,
    selectedQuote: Quote?,
    onSearchQueryChange: (String) -> Unit,
    onSelectSymbol: (SymbolMatch) -> Unit,
    onClearSymbol: () -> Unit,
    onSymbolChange: (String) -> Unit,
    onBuyZoneLowChange: (String) -> Unit,
    onBuyZoneHighChange: (String) -> Unit,
    onStrongBuyChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onApplyPresetBuyZone: (Double) -> Unit,
    onApplyPresetStrongBuy: (Double) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val low = buyZoneLow.toDoubleOrNull()
    val high = buyZoneHigh.toDoubleOrNull()
    val strong = strongBuy.toDoubleOrNull()
    val canSave = symbol.isNotBlank() &&
        low != null &&
        high != null &&
        low > 0 &&
        high > 0 &&
        low <= high &&
        (strong == null || strong <= low)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEditing) "Edit buy zone" else "Add buy zone",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedSymbol == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text("Search symbol") },
                        placeholder = { Text("e.g. Apple, AAPL") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isSearching) {
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                items(searchResults) { match ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectSymbol(match) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                match.symbol,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            match.name?.let {
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    selectedSymbol.symbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                selectedSymbol.name?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                selectedQuote?.let { quote ->
                                    Text(
                                        "Current: ${formatPrice(quote.price)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = onClearSymbol) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                }

                if (selectedSymbol != null) {
                    Text(
                        "Quick-set buy zone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5.0, 10.0, 20.0, 30.0, 40.0).forEach { pct ->
                            AssistChip(
                                onClick = { onApplyPresetBuyZone(pct) },
                                label = { Text("${pct.toInt()}%") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = buyZoneLow,
                        onValueChange = onBuyZoneLowChange,
                        label = { Text("Zone low") },
                        placeholder = { Text("92.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        enabled = selectedSymbol != null || isEditing
                    )
                    OutlinedTextField(
                        value = buyZoneHigh,
                        onValueChange = onBuyZoneHighChange,
                        label = { Text("Zone high") },
                        placeholder = { Text("96.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        enabled = selectedSymbol != null || isEditing
                    )
                }

                if (low != null && low > 0) {
                    AssistChip(
                        onClick = { onApplyPresetStrongBuy(10.0) },
                        label = { Text("Set strong buy 10% below zone low") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = strongBuy,
                    onValueChange = onStrongBuyChange,
                    label = { Text("Strong buy below (optional)") },
                    placeholder = { Text("88.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedSymbol != null || isEditing
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notes / thesis (optional)") },
                    placeholder = { Text("e.g. good ETF, wait for pullback") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedSymbol != null || isEditing
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(if (isEditing) "Save" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
