package com.stockwatchdog.app.ui.diptracker

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                        Text("Dip Tracker", fontWeight = FontWeight.SemiBold)
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
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (state.rows.isNotEmpty()) {
                    item { SummaryCard(state.rows) }
                }
                items(state.rows, key = { it.entity.id }) { row ->
                    DipRowItem(
                        row = row,
                        onClick = { onOpenSymbol(row.entity.symbol) },
                        onEdit = { vm.openEditDialog(row.entity.id) },
                        onDelete = { vm.confirmDelete(row.entity.id) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }

    // ── Add/Edit dialog ────────────────────────────────────────────────
    if (state.dialogOpen) {
        EditDipDialog(
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Buy opportunities",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryChip(count = inZone, label = "In zone", color = InBuyZoneColor)
                SummaryChip(count = near, label = "Near zone", color = NearZoneColor)
                SummaryChip(count = total, label = "Tracking", color = AboveZoneColor)
            }
        }
    }
}

@Composable
private fun SummaryChip(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$count",
            style = MaterialTheme.typography.headlineSmall,
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
