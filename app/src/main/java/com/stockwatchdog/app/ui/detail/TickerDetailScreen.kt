package com.stockwatchdog.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
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
                    alertDao = container.database.alertDao(),
                    positionLotDao = container.database.positionLotDao()
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
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        val tabTitles = listOf("Position", "Alerts")

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Fixed hero: price, range, chart, stats
            Column(Modifier.padding(horizontal = 16.dp)) {
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
                Spacer(Modifier.height(12.dp))
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    val label = if (index == 1 && alerts.isNotEmpty())
                        "$title \u00B7 ${alerts.size}"
                    else title
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) }
                    )
                }
            }

            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (selectedTab) {
                    0 -> PositionSection(
                        state = state,
                        onAddLot = { vm.openAddLot() },
                        onEditLot = { id -> vm.openEditLot(id) },
                        onDeleteLot = { id -> vm.confirmDeleteLot(id) },
                        onTakeProfit = { pct -> vm.createTakeProfitAlert(pct) },
                        onStopLoss = { pct -> vm.createStopLossAlert(pct) }
                    )
                    1 -> AlertsSection(
                        alerts = alerts,
                        onCreate = { vm.openCreateAlert() },
                        onToggle = vm::toggleAlert,
                        onDelete = vm::confirmDeleteAlert
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (state.createAlertOpen) {
        CreateAlertDialog(
            type = state.newAlertType,
            threshold = state.newAlertThreshold,
            hasEntryPrice = state.avgEntryPrice != null,
            onTypeChange = vm::onAlertTypeChange,
            onThresholdChange = vm::onAlertThresholdChange,
            onSave = { vm.saveAlert() },
            onDismiss = { vm.closeCreateAlert() }
        )
    }

    if (state.lotDialogOpen) {
        EditPositionDialog(
            entryPriceDraft = state.lotDialogPriceDraft,
            amountInvestedDraft = state.lotDialogAmountDraft,
            isEditing = state.lotDialogEditingId != null,
            onEntryChange = vm::onLotPriceDraftChange,
            onAmountChange = vm::onLotAmountDraftChange,
            onSave = { vm.saveLot() },
            onDismiss = { vm.closeLotDialog() }
        )
    }

    state.lotDeleteConfirmId?.let { lotId ->
        AlertDialog(
            onDismissRequest = { vm.cancelDeleteLot() },
            title = { Text("Delete this position?") },
            text = {
                Text(
                    "This will remove the entry point and its invested amount. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteLot(lotId) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDeleteLot() }) { Text("Cancel") }
            }
        )
    }

    state.alertDeleteConfirmId?.let { alertId ->
        val alert = alerts.firstOrNull { it.id == alertId }
        AlertDialog(
            onDismissRequest = { vm.cancelDeleteAlert() },
            title = { Text("Delete alert?") },
            text = {
                Text(
                    if (alert != null) "Delete \"${describeAlert(alert)}\"?"
                    else "Delete this alert?"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteAlert(alertId) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelDeleteAlert() }) { Text("Cancel") }
            }
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
        TextButton(
            onClick = { onDelete(a.id) },
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) { Text("Delete") }
    }
}

@Composable
private fun PositionSection(
    state: DetailUiState,
    onAddLot: () -> Unit,
    onEditLot: (Long) -> Unit,
    onDeleteLot: (Long) -> Unit,
    onTakeProfit: (Double) -> Unit = {},
    onStopLoss: (Double) -> Unit = {}
) {
    val pnl = PositionCalculator.calculate(
        currentPrice = state.quote?.price,
        lots = state.lots
    )
    val lotPnlById = pnl.perLot.associateBy { it.lotId }

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
                TextButton(onClick = onAddLot) {
                    Text(if (state.lots.isEmpty()) "Add position" else "Add another")
                }
            }

            if (state.lots.isEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Add your entry point and the amount you invested to track " +
                        "total gain/loss and unlock gain/loss alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(6.dp))
                SummaryRow(
                    "Total invested", formatPrice(pnl.totalInvested),
                    "Current value", formatPrice(pnl.positionValue)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
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
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            "Return",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatSignedPercent(pnl.percentPnl),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = changeColor(pnl.percentPnl)
                        )
                    }
                }

                state.lots.forEachIndexed { index, lot ->
                    Spacer(Modifier.height(12.dp))
                    LotCard(
                        title = "Position ${index + 1}",
                        lot = lot,
                        pnl = lotPnlById[lot.id],
                        onEdit = { onEditLot(lot.id) },
                        onDelete = { onDeleteLot(lot.id) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                TpSlShortcuts(
                    hasEntryPrice = state.avgEntryPrice != null,
                    onTakeProfit = onTakeProfit,
                    onStopLoss = onStopLoss
                )
            }
        }
    }
}

@Composable
private fun LotCard(
    title: String,
    lot: PositionLotEntity,
    pnl: com.stockwatchdog.app.domain.LotPnl?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(
                        onClick = onDelete,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete") }
                }
            }
            SummaryRow(
                "Entry point", formatPrice(lot.entryPrice),
                "Amount invested", formatPrice(lot.amountInvested)
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Since entry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatSignedChange(pnl?.pnl),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor(pnl?.pnl)
                    )
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "Return",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatSignedPercent(pnl?.percentPnl),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = changeColor(pnl?.percentPnl)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPositionDialog(
    entryPriceDraft: String,
    amountInvestedDraft: String,
    isEditing: Boolean,
    onEntryChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val entryOk = entryPriceDraft.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true
    val amountOk = amountInvestedDraft.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit position" else "Add position") },
        text = {
            Column {
                OutlinedTextField(
                    value = entryPriceDraft,
                    onValueChange = onEntryChange,
                    label = { Text("Entry point") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountInvestedDraft,
                    onValueChange = onAmountChange,
                    label = { Text("Amount invested") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter the price per share you bought at and the total amount " +
                        "invested at this entry. Add another position later if you buy more.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = entryOk && amountOk
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TpSlShortcuts(
    hasEntryPrice: Boolean,
    onTakeProfit: (Double) -> Unit,
    onStopLoss: (Double) -> Unit
) {
    if (!hasEntryPrice) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Quick alerts",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTakeProfit(10.0) }) { Text("TP +10%") }
            OutlinedButton(onClick = { onTakeProfit(20.0) }) { Text("TP +20%") }
            OutlinedButton(onClick = { onStopLoss(5.0) }) { Text("SL -5%") }
            OutlinedButton(onClick = { onStopLoss(10.0) }) { Text("SL -10%") }
        }
    }
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
