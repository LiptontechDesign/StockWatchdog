package com.stockwatchdog.app.ui.detail

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.domain.ChartRange
import com.stockwatchdog.app.domain.PositionCalculator
import com.stockwatchdog.app.ui.components.PriceLineChart
import com.stockwatchdog.app.ui.components.changeColor
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.components.formatSignedChange
import com.stockwatchdog.app.ui.components.formatSignedPercent
import com.stockwatchdog.app.ui.components.formatVolume

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickerDetailScreen(
    container: AppContainer,
    symbol: String,
    onBack: () -> Unit
) {
    val vm: TickerDetailViewModel = viewModel(
        key = "detail-$symbol",
        factory = viewModelFactory {
            initializer {
                TickerDetailViewModel(
                    symbol = symbol,
                    repo = container.marketDataRepository,
                    watchlistDao = container.database.watchlistDao(),
                    alertDao = container.database.alertDao()
                )
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.symbol, fontWeight = FontWeight.SemiBold)
                        state.quote?.name?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchlist() }) {
                        Icon(
                            imageVector = if (state.inWatchlist) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Toggle watchlist",
                            tint = if (state.inWatchlist)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        if (state.quoteLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            PriceHeader(state)
            Spacer(Modifier.height(8.dp))

            RangeSelector(
                selected = state.range,
                onSelect = { vm.selectRange(it) }
            )

            Spacer(Modifier.height(12.dp))
            ChartArea(state)
            Spacer(Modifier.height(16.dp))
            SummaryGrid(state)
            Spacer(Modifier.height(16.dp))

            PositionSection(
                state = state,
                onEdit = { vm.openEditPosition() }
            )
            Spacer(Modifier.height(16.dp))

            AlertsSection(
                alerts = alerts,
                onCreate = { vm.openCreateAlert() },
                onToggle = vm::toggleAlert,
                onDelete = vm::deleteAlert
            )
        }
    }

    if (state.createAlertOpen) {
        CreateAlertDialog(
            type = state.newAlertType,
            threshold = state.newAlertThreshold,
            hasEntryPrice = state.entryPrice != null,
            onTypeChange = vm::onAlertTypeChange,
            onThresholdChange = vm::onAlertThresholdChange,
            onSave = { vm.saveAlert() },
            onDismiss = { vm.closeCreateAlert() }
        )
    }

    if (state.editPositionOpen) {
        EditPositionDialog(
            entryPriceDraft = state.entryPriceDraft,
            quantityDraft = state.quantityDraft,
            notesDraft = state.notesDraft,
            onEntryChange = vm::onEntryPriceDraftChange,
            onQtyChange = vm::onQuantityDraftChange,
            onNotesChange = vm::onNotesDraftChange,
            onSave = { vm.savePosition() },
            onClear = { vm.clearPosition() },
            onDismiss = { vm.closeEditPosition() }
        )
    }
}

@Composable
private fun PriceHeader(state: DetailUiState) {
    val q = state.quote
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            formatPrice(q?.price, q?.currency),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatSignedChange(q?.change)} (${formatSignedPercent(q?.percentChange)})",
                style = MaterialTheme.typography.titleSmall,
                color = changeColor(q?.percentChange),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.size(8.dp))
            q?.marketIsOpen?.let { open ->
                AssistChip(
                    onClick = { },
                    label = { Text(if (open) "Market open" else "Market closed") },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
        }
        if (!state.quoteError.isNullOrBlank() && q == null) {
            Text(
                state.quoteError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSelector(
    selected: ChartRange,
    onSelect: (ChartRange) -> Unit
) {
    val options = listOf(ChartRange.ONE_DAY, ChartRange.FIVE_DAYS, ChartRange.ONE_MONTH, ChartRange.THREE_MONTHS)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(range.label)
            }
        }
    }
}

@Composable
private fun ChartArea(state: DetailUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            when {
                state.chartLoading && state.points.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
                !state.chartError.isNullOrBlank() && state.points.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            state.chartError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    PriceLineChart(
                        points = state.points,
                        isPositive = state.quote?.percentChange?.let { it >= 0 }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryGrid(state: DetailUiState) {
    val q = state.quote
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            SummaryRow("Open", formatPrice(q?.open), "Prev close", formatPrice(q?.previousClose))
            Spacer(Modifier.height(6.dp))
            SummaryRow("Day high", formatPrice(q?.high), "Day low", formatPrice(q?.low))
            Spacer(Modifier.height(6.dp))
            SummaryRow("Volume", formatVolume(q?.volume), "Currency", q?.currency ?: "--")
        }
    }
}

@Composable
private fun SummaryRow(l1: String, v1: String, l2: String, v2: String) {
    Row(Modifier.fillMaxWidth()) {
        SummaryCell(l1, v1, Modifier.weight(1f))
        Spacer(Modifier.size(16.dp))
        SummaryCell(l2, v2, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AlertsSection(
    alerts: List<AlertEntity>,
    onCreate: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onCreate) { Text("New alert") }
        }
        Text(
            "Price above / below • % day • Gain or Loss vs entry",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        if (alerts.isEmpty()) {
            Text(
                "No alerts for this ticker yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            alerts.forEach { a ->
                AlertRow(a, onToggle = onToggle, onDelete = onDelete)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

@Composable
private fun AlertRow(
    a: AlertEntity,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(describeAlert(a), style = MaterialTheme.typography.bodyLarge)
            if (!a.enabled) {
                Text(
                    "disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = { onToggle(a.id, !a.enabled) }) {
            Text(if (a.enabled) "Disable" else "Enable")
        }
        TextButton(onClick = { onDelete(a.id) }) { Text("Delete") }
    }
}

@Composable
private fun PositionSection(
    state: DetailUiState,
    onEdit: () -> Unit
) {
    val pnl = PositionCalculator.calculate(
        currentPrice = state.quote?.price,
        entryPrice = state.entryPrice,
        quantity = state.quantity
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Your position",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onEdit) {
                    Text(if (state.entryPrice == null) "Add position" else "Edit")
                }
            }

            if (state.entryPrice == null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Add your entry price to see P&L and unlock Gain/Loss vs entry alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(6.dp))
                SummaryRow(
                    "Entry price", formatPrice(state.entryPrice),
                    "Quantity", state.quantity?.let { formatQuantity(it) } ?: "—"
                )
                Spacer(Modifier.height(8.dp))

                val perShareText = pnl.perSharePnl?.let { formatSignedChange(it) } ?: "—"
                val pctText = formatSignedPercent(pnl.percentPnl)
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Since entry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$perShareText ($pctText)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = changeColor(pnl.percentPnl)
                        )
                    }
                    if (pnl.totalPnl != null) {
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                "Total P&L",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatSignedChange(pnl.totalPnl),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = changeColor(pnl.totalPnl)
                            )
                        }
                    }
                }

                if (pnl.positionValue != null || pnl.costBasis != null) {
                    Spacer(Modifier.height(8.dp))
                    SummaryRow(
                        "Position value", formatPrice(pnl.positionValue),
                        "Cost basis", formatPrice(pnl.costBasis)
                    )
                }

                if (!state.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatQuantity(q: Double): String =
    if (q % 1.0 == 0.0) "%.0f".format(q) else "%.4f".format(q).trimEnd('0').trimEnd('.')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPositionDialog(
    entryPriceDraft: String,
    quantityDraft: String,
    notesDraft: String,
    onEntryChange: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your position") },
        text = {
            Column {
                OutlinedTextField(
                    value = entryPriceDraft,
                    onValueChange = onEntryChange,
                    label = { Text("Entry price (required)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantityDraft,
                    onValueChange = onQtyChange,
                    label = { Text("Quantity (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = onNotesChange,
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Entry price and quantity support decimals. Leave quantity " +
                        "blank if you only want % vs entry.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = entryPriceDraft.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

fun describeAlert(a: AlertEntity): String = when (a.type) {
    AlertType.PRICE_ABOVE -> "Price above ${"%.2f".format(a.threshold)}"
    AlertType.PRICE_BELOW -> "Price below ${"%.2f".format(a.threshold)}"
    AlertType.PERCENT_CHANGE_DAY -> "Day change exceeds ${"%.2f".format(a.threshold)}%"
    AlertType.PERCENT_ABOVE_ENTRY -> "Gain vs entry reaches +${"%.2f".format(a.threshold)}%"
    AlertType.PERCENT_BELOW_ENTRY -> "Loss vs entry reaches -${"%.2f".format(a.threshold)}%"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAlertDialog(
    type: AlertType,
    threshold: String,
    hasEntryPrice: Boolean,
    onTypeChange: (AlertType) -> Unit,
    onThresholdChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEntryBased = type == AlertType.PERCENT_ABOVE_ENTRY ||
        type == AlertType.PERCENT_BELOW_ENTRY
    val isPercent = type == AlertType.PERCENT_CHANGE_DAY || isEntryBased
    val thresholdValid = threshold.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New alert") },
        text = {
            Column {
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row {
                    FilterChip(
                        selected = type == AlertType.PRICE_ABOVE,
                        onClick = { onTypeChange(AlertType.PRICE_ABOVE) },
                        label = { Text("Above") }
                    )
                    Spacer(Modifier.size(6.dp))
                    FilterChip(
                        selected = type == AlertType.PRICE_BELOW,
                        onClick = { onTypeChange(AlertType.PRICE_BELOW) },
                        label = { Text("Below") }
                    )
                    Spacer(Modifier.size(6.dp))
                    FilterChip(
                        selected = type == AlertType.PERCENT_CHANGE_DAY,
                        onClick = { onTypeChange(AlertType.PERCENT_CHANGE_DAY) },
                        label = { Text("% day") }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    FilterChip(
                        selected = type == AlertType.PERCENT_ABOVE_ENTRY,
                        onClick = { onTypeChange(AlertType.PERCENT_ABOVE_ENTRY) },
                        enabled = hasEntryPrice,
                        label = { Text("Gain vs entry") }
                    )
                    Spacer(Modifier.size(6.dp))
                    FilterChip(
                        selected = type == AlertType.PERCENT_BELOW_ENTRY,
                        onClick = { onTypeChange(AlertType.PERCENT_BELOW_ENTRY) },
                        enabled = hasEntryPrice,
                        label = { Text("Loss vs entry") }
                    )
                }
                if (!hasEntryPrice) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Entry-based alerts need an entry price. " +
                            "Close this and tap \"Add position\" first.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = threshold,
                    onValueChange = onThresholdChange,
                    label = {
                        Text(
                            when (type) {
                                AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW ->
                                    "Price"
                                AlertType.PERCENT_CHANGE_DAY ->
                                    "Percent (e.g. 3.5)"
                                AlertType.PERCENT_ABOVE_ENTRY ->
                                    "Gain % (e.g. 10 for +10%)"
                                AlertType.PERCENT_BELOW_ENTRY ->
                                    "Loss % (e.g. 5 for -5%)"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (isPercent) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Use a positive number. For \"Loss vs entry 5%\" enter 5.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = thresholdValid && (!isEntryBased || hasEntryPrice)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
