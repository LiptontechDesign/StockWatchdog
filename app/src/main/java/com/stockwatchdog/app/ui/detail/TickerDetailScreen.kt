package com.stockwatchdog.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.data.db.entities.PositionLotEntity
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.domain.ChartRange
import com.stockwatchdog.app.domain.PositionCalculator
import com.stockwatchdog.app.ui.alerts.AlertEditDialog
import com.stockwatchdog.app.ui.components.PriceLineChart
import com.stockwatchdog.app.ui.components.changeColor
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.components.formatSignedChange
import com.stockwatchdog.app.ui.components.formatSignedPercent
import com.stockwatchdog.app.ui.components.formatVolume
import com.stockwatchdog.app.util.MarketClock
import java.time.Instant
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickerDetailScreen(
    container: AppContainer,
    symbol: String,
    initialTab: Int = 0,
    onBack: () -> Unit
) {
    val vm: TickerDetailViewModel = viewModel(
        key = "detail-$symbol",
        factory = viewModelFactory {
            initializer {
                TickerDetailViewModel(
                    symbol = symbol,
                    repo = container.marketDataRepository,
                    detailsRepo = container.stockDetailsRepository,
                    watchlistDao = container.database.watchlistDao(),
                    alertDao = container.database.alertDao(),
                    positionLotDao = container.database.positionLotDao(),
                    settingsRepository = container.settingsRepository
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
        val tabTitles = listOf("Position", "Alerts", "Financials")
        var selectedTab by rememberSaveable(symbol, initialTab) {
            mutableIntStateOf(initialTab.coerceIn(tabTitles.indices))
        }

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
                    2 -> FinancialsSection(state = state)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (state.createAlertOpen) {
        AlertEditDialog(
            isEditing = false,
            symbolLocked = true,
            symbol = state.symbol,
            type = state.newAlertType,
            threshold = state.newAlertThreshold,
            notes = state.newAlertNotes,
            autoDisable = state.newAlertAutoDisable,
            marketHoursOnly = state.newAlertMarketHoursOnly,
            hasEntryPrice = state.avgEntryPrice != null,
            companyName = state.quote?.name,
            currentPrice = state.quote?.price,
            percentChange = state.quote?.percentChange,
            currency = state.quote?.currency,
            onSymbolChange = { },
            onTypeChange = vm::onAlertTypeChange,
            onThresholdChange = vm::onAlertThresholdChange,
            onNotesChange = vm::onAlertNotesChange,
            onAutoDisableChange = vm::onAlertAutoDisableChange,
            onMarketHoursOnlyChange = vm::onAlertMarketHoursOnlyChange,
            onSave = { vm.saveAlert() },
            onDismiss = { vm.closeCreateAlert() }
        )
    }

    if (state.lotDialogOpen) {
        val existingPlatforms = state.lots.mapNotNull { it.platform }.distinct()
        EditPositionDialog(
            symbol = state.symbol,
            companyName = state.quote?.name,
            currentPrice = state.quote?.price,
            percentChange = state.quote?.percentChange,
            currency = state.quote?.currency,
            entryPriceDraft = state.lotDialogPriceDraft,
            amountInvestedDraft = state.lotDialogAmountDraft,
            platformDraft = state.lotDialogPlatformDraft,
            existingPlatforms = existingPlatforms,
            isEditing = state.lotDialogEditingId != null,
            onEntryChange = vm::onLotPriceDraftChange,
            onAmountChange = vm::onLotAmountDraftChange,
            onPlatformChange = vm::onLotPlatformDraftChange,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
private fun FinancialsSection(state: DetailUiState) {
    val details = state.details
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Financials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (state.detailsLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        if (details == null) {
            Text(
                state.detailsError ?: "Financial data is loading.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        FinancialPunchSummary(details = details, currentPrice = state.quote?.price)
        AnalystConsensusCard(details = details, currentPrice = state.quote?.price)

        FinancialMetricCard("Results") {
            FinancialMetricRow(
                "Next results",
                formatResultDate(details),
                "Last EPS",
                formatEpsSurprise(details),
                tone2 = toneForSigned(details.epsSurprisePct())
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                revenueResultLabel(details),
                formatRevenueSurprise(details),
                "Report period",
                details.latestFinancialPeriod ?: details.lastEpsQuarterLabel ?: "--",
                tone1 = toneForSigned(details.revenueSurprisePct())
            )
        }

        FinancialMetricCard("Growth") {
            FinancialMetricRow(
                "Revenue growth",
                formatSignedPercentHint(details.revenueGrowthPct, ""),
                "EPS growth",
                formatSignedPercentHint(details.epsGrowthPct, ""),
                tone1 = toneForSigned(details.revenueGrowthPct),
                tone2 = toneForSigned(details.epsGrowthPct)
            )
        }

        FinancialMetricCard("Profitability") {
            FinancialMetricRow(
                "Revenue",
                formatCompactMoneyHint(details.totalRevenue, ""),
                "Net margin",
                formatPercentHint(details.profitMarginPct, ""),
                tone2 = toneForMargin(details.profitMarginPct)
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                "Operating margin",
                formatPercentHint(details.operatingMarginPct, ""),
                "Free cash flow",
                formatCompactMoneyHint(details.freeCashflow, ""),
                tone1 = toneForMargin(details.operatingMarginPct),
                tone2 = toneForSigned(details.freeCashflow)
            )
        }

        FinancialMetricCard("Safety") {
            FinancialMetricRow(
                "Debt/equity",
                formatRatioHint(details.debtToEquity, ""),
                "Total debt",
                formatCompactMoneyHint(details.totalDebt, ""),
                tone1 = toneForDebt(details.debtToEquity)
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                "Cash",
                formatCompactMoneyHint(details.totalCash, ""),
                "Current ratio",
                formatRatioHint(details.currentRatio, ""),
                tone2 = toneForCurrentRatio(details.currentRatio)
            )
        }

        FinancialMetricCard("Valuation") {
            FinancialMetricRow(
                "P/E",
                formatMultipleHint(details.trailingPe, ""),
                "Forward P/E",
                formatMultipleHint(details.forwardPe, ""),
                tone1 = toneForPe(details.trailingPe),
                tone2 = toneForPe(details.forwardPe)
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                "EPS TTM",
                formatPlainNumberHint(details.epsTtm, ""),
                "Analyst target",
                formatPrice(details.analystTargetMean)
            )
        }
    }
}

@Composable
private fun AnalystConsensusCard(details: StockDetails, currentPrice: Double?) {
    val label = analystConsensusLabel(details)
    val total = analystVoteTotal(details)
    val target = details.analystTargetMean
    if (label == null && total <= 0) return

    FinancialMetricCard("Analyst consensus") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = toneForAnalystConsensus(details)?.let { financialToneColor(it) }
                        ?: MaterialTheme.colorScheme.onSurface
                )
                Text(
                    analystConsensusSubline(details),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (target != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(target),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        details.upsideToTargetPct(currentPrice)?.let { "Target ${"%+.0f%%".format(it)}" }
                            ?: "Target",
                        style = MaterialTheme.typography.labelSmall,
                        color = details.upsideToTargetPct(currentPrice)
                            ?.let { changeColor(it) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            AnalystVoteBar(details)
            Spacer(Modifier.height(6.dp))
            Text(
                analystVoteLine(details),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnalystVoteBar(details: StockDetails) {
    val counts = listOf(
        details.analystStrongBuyCount ?: 0,
        details.analystBuyCount ?: 0,
        details.analystHoldCount ?: 0,
        details.analystSellCount ?: 0,
        details.analystStrongSellCount ?: 0
    )
    val colors = listOf(
        changeColor(1.0),
        changeColor(0.7),
        androidx.compose.ui.graphics.Color(0xFFE0A72E),
        changeColor(-0.7),
        changeColor(-1.0)
    )
    Row(Modifier.fillMaxWidth().height(8.dp)) {
        counts.forEachIndexed { index, count ->
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .height(8.dp)
                        .background(colors[index])
                )
            }
        }
    }
}

@Composable
private fun FinancialPunchSummary(details: StockDetails, currentPrice: Double?) {
    val pulse = buildFinancialPulse(details, currentPrice)
    val chips = buildSignalChips(details, currentPrice)
    val coverage = listOfNotNull(
        details.revenueGrowthPct,
        details.profitMarginPct,
        details.debtToEquity,
        details.trailingPe,
        details.epsGrowthPct
    ).size
    val partial = coverage < 3

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(financialToneColor(pulse.tone), RoundedCornerShape(50))
            )
            Spacer(Modifier.size(8.dp))
            Text(
                pulse.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        if (chips.isNotEmpty()) {
            chips.chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.forEach { SignalChip(it) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val parts = buildList {
                details.financialDataSource?.let { add(if (it == "FMP") "FMP" else it) }
                details.latestFinancialPeriod?.let { add(it) }
                if (partial) add("partial data")
            }
            if (parts.isNotEmpty()) {
                Text(
                    parts.joinToString(" \u00B7 "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (partial) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (partial) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

private data class SignalChipData(val text: String, val tone: FinancialTone)

@Composable
private fun SignalChip(data: SignalChipData) {
    val color = financialToneColor(data.tone)
    Text(
        data.text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

private fun buildSignalChips(
    details: StockDetails,
    currentPrice: Double?
): List<SignalChipData> = buildList {
    details.revenueGrowthPct?.let {
        add(SignalChipData(
            "Rev %+.0f%%".format(it),
            if (it >= 0) FinancialTone.GOOD else FinancialTone.BAD
        ))
    }
    details.epsGrowthPct?.let {
        add(SignalChipData(
            "EPS %+.0f%%".format(it),
            if (it >= 0) FinancialTone.GOOD else FinancialTone.BAD
        ))
    }
    details.profitMarginPct?.let {
        val tone = when {
            it >= 15.0 -> FinancialTone.GOOD
            it > 0.0 -> FinancialTone.WATCH
            else -> FinancialTone.BAD
        }
        add(SignalChipData("Margin %.0f%%".format(it), tone))
    }
    details.debtToEquity?.let {
        val tone = when {
            it <= 100.0 -> FinancialTone.GOOD
            it <= 200.0 -> FinancialTone.WATCH
            else -> FinancialTone.BAD
        }
        val label = when {
            it <= 100.0 -> "Debt low"
            it <= 200.0 -> "Debt high"
            else -> "Debt heavy"
        }
        add(SignalChipData(label, tone))
    }
    details.trailingPe?.takeIf { it > 0.0 }?.let {
        val tone = when {
            it < 18.0 -> FinancialTone.GOOD
            it <= 35.0 -> FinancialTone.WATCH
            else -> FinancialTone.BAD
        }
        val tag = when {
            it < 18.0 -> "low"
            it <= 35.0 -> "fair"
            else -> "rich"
        }
        add(SignalChipData("P/E %.0f %s".format(it, tag), tone))
    }
    details.upsideToTargetPct(currentPrice)?.let {
        add(SignalChipData(
            "Target %+.0f%%".format(it),
            if (it >= 0) FinancialTone.GOOD else FinancialTone.BAD
        ))
    }
}

@Composable
private fun FinancialMetricCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun FinancialMetricRow(
    label1: String,
    value1: String,
    label2: String,
    value2: String,
    tone1: FinancialTone? = null,
    tone2: FinancialTone? = null
) {
    Row(Modifier.fillMaxWidth()) {
        FinancialMetricCell(label1, value1, tone1, Modifier.weight(1f))
        Spacer(Modifier.size(16.dp))
        FinancialMetricCell(label2, value2, tone2, Modifier.weight(1f))
    }
}

@Composable
private fun FinancialMetricCell(
    label: String,
    value: String,
    tone: FinancialTone?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = tone?.let { financialToneColor(it) } ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private enum class FinancialTone { GOOD, WATCH, BAD, NEUTRAL }

private data class FinancialPulse(
    val text: String,
    val tone: FinancialTone
)

private fun buildFinancialPulse(details: StockDetails, currentPrice: Double?): FinancialPulse {
    val parts = mutableListOf<String>()
    details.revenueGrowthPct?.let {
        parts += if (it >= 0) "sales growing" else "sales falling"
    }
    details.epsGrowthPct?.let {
        parts += if (it >= 0) "EPS improving" else "EPS weakening"
    }
    details.profitMarginPct?.let {
        parts += when {
            it >= 20.0 -> "strong margin"
            it > 0.0 -> "positive margin"
            else -> "margin pressure"
        }
    }
    details.debtToEquity?.let {
        parts += if (it <= 100.0) "debt looks manageable" else "debt is elevated"
    }
    details.trailingPe?.let {
        parts += when {
            it <= 0.0 -> "valuation unclear"
            it < 18.0 -> "valuation modest"
            it <= 35.0 -> "valuation fair"
            else -> "valuation rich"
        }
    }
    details.upsideToTargetPct(currentPrice)?.let {
        parts += if (it >= 0) "analysts see upside" else "target sits below price"
    }

    val text = parts
        .take(4)
        .joinToString(separator = ", ")
        .ifBlank { "limited data; watch the next results date before deciding" }
        .replaceFirstChar { it.uppercaseChar() }
    val negatives = listOfNotNull(
        details.revenueGrowthPct?.takeIf { it < 0.0 },
        details.epsGrowthPct?.takeIf { it < 0.0 },
        details.profitMarginPct?.takeIf { it <= 0.0 },
        details.debtToEquity?.takeIf { it > 150.0 }
    ).size
    val positives = listOfNotNull(
        details.revenueGrowthPct?.takeIf { it > 0.0 },
        details.epsGrowthPct?.takeIf { it > 0.0 },
        details.profitMarginPct?.takeIf { it > 10.0 },
        details.debtToEquity?.takeIf { it <= 100.0 }
    ).size
    val tone = when {
        negatives >= 2 -> FinancialTone.BAD
        positives >= 2 -> FinancialTone.GOOD
        parts.isEmpty() -> FinancialTone.WATCH
        else -> FinancialTone.WATCH
    }
    return FinancialPulse(text, tone)
}

@Composable
private fun financialToneColor(tone: FinancialTone) = when (tone) {
    FinancialTone.GOOD -> changeColor(1.0)
    FinancialTone.BAD -> changeColor(-1.0)
    FinancialTone.WATCH -> androidx.compose.ui.graphics.Color(0xFFE0A72E)
    FinancialTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
}

private fun toneForSigned(value: Double?): FinancialTone? = value?.let {
    when {
        it > 0.0 -> FinancialTone.GOOD
        it < 0.0 -> FinancialTone.BAD
        else -> FinancialTone.NEUTRAL
    }
}

private fun toneForMargin(value: Double?): FinancialTone? = value?.let {
    when {
        it >= 20.0 -> FinancialTone.GOOD
        it > 0.0 -> FinancialTone.WATCH
        else -> FinancialTone.BAD
    }
}

private fun toneForDebt(value: Double?): FinancialTone? = value?.let {
    when {
        it <= 100.0 -> FinancialTone.GOOD
        it <= 150.0 -> FinancialTone.WATCH
        else -> FinancialTone.BAD
    }
}

private fun toneForCurrentRatio(value: Double?): FinancialTone? = value?.let {
    when {
        it >= 1.5 -> FinancialTone.GOOD
        it >= 1.0 -> FinancialTone.WATCH
        else -> FinancialTone.BAD
    }
}

private fun toneForPe(value: Double?): FinancialTone? = value?.let {
    when {
        it <= 0.0 -> FinancialTone.NEUTRAL
        it < 18.0 -> FinancialTone.GOOD
        it <= 35.0 -> FinancialTone.WATCH
        else -> FinancialTone.BAD
    }
}

private fun toneForAnalystConsensus(details: StockDetails): FinancialTone? {
    val label = analystConsensusLabel(details)?.lowercase() ?: return null
    val positivePct = analystPositivePct(details)
    return when {
        "sell" in label -> FinancialTone.BAD
        label == "hold" -> FinancialTone.WATCH
        positivePct != null && positivePct >= 60.0 -> FinancialTone.GOOD
        positivePct != null && positivePct >= 45.0 -> FinancialTone.WATCH
        positivePct != null -> FinancialTone.BAD
        "buy" in label -> FinancialTone.GOOD
        else -> FinancialTone.WATCH
    }
}

private fun formatEpsSurprise(details: StockDetails): String {
    val actual = details.lastEpsActual ?: return "--"
    val estimate = details.lastEpsEstimate
    val surprise = details.epsSurprisePct()
    return when {
        surprise != null -> {
            val label = if (surprise >= 0) "beat" else "miss"
            "${formatPlainNumber(actual)} ($label ${formatPercentOne(kotlin.math.abs(surprise))})"
        }
        estimate != null -> "${formatPlainNumber(actual)} vs ${formatPlainNumber(estimate)}"
        else -> formatPlainNumber(actual)
    }
}

private fun formatRevenueSurprise(details: StockDetails): String {
    val actual = details.lastRevenueActual ?: return "--"
    val estimate = details.lastRevenueEstimate
    val surprise = details.revenueSurprisePct()
    return when {
        surprise != null -> {
            val label = if (surprise >= 0) "beat" else "miss"
            "${formatCompactMoney(actual)} ($label ${formatPercentOne(kotlin.math.abs(surprise))})"
        }
        estimate != null -> "${formatCompactMoney(actual)} vs ${formatCompactMoney(estimate)}"
        else -> formatCompactMoney(actual)
    }
}

private fun revenueResultLabel(details: StockDetails): String =
    if (details.lastRevenueEstimate != null) "Revenue surprise" else "Revenue actual"

private fun analystConsensusLabel(details: StockDetails): String? =
    when (details.analystRecommendation?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_")) {
        "strong_buy", "strongbuy" -> "Strong buy"
        "buy" -> "Buy"
        "hold" -> "Hold"
        "sell" -> "Sell"
        "strong_sell", "strongsell" -> "Strong sell"
        null, "" -> null
        else -> details.analystRecommendation
            ?.trim()
            ?.replace("_", " ")
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

private fun analystVoteTotal(details: StockDetails): Int =
    listOf(
        details.analystStrongBuyCount,
        details.analystBuyCount,
        details.analystHoldCount,
        details.analystSellCount,
        details.analystStrongSellCount
    ).sumOf { it ?: 0 }

private fun analystPositiveVotes(details: StockDetails): Int =
    (details.analystStrongBuyCount ?: 0) + (details.analystBuyCount ?: 0)

private fun analystPositivePct(details: StockDetails): Double? {
    val total = analystVoteTotal(details)
    if (total <= 0) return null
    return analystPositiveVotes(details).toDouble() / total.toDouble() * 100.0
}

private fun analystConsensusSubline(details: StockDetails): String {
    val total = analystVoteTotal(details)
    val positivePct = analystPositivePct(details)
    val period = details.analystConsensusPeriod
    return buildList {
        if (total > 0) add("${positivePct?.let { "%.0f%%".format(it) } ?: "--"} positive")
        val analystCount = total.takeIf { it > 0 }?.toLong() ?: details.analystOpinionsCount
        analystCount?.takeIf { it > 0 }?.let { add("$it analysts") }
        if (!period.isNullOrBlank()) add(period)
    }.takeIf { it.isNotEmpty() }?.joinToString(" / ") ?: "Latest available analyst view"
}

private fun analystVoteLine(details: StockDetails): String {
    val strongBuy = details.analystStrongBuyCount ?: 0
    val buy = details.analystBuyCount ?: 0
    val hold = details.analystHoldCount ?: 0
    val sell = details.analystSellCount ?: 0
    val strongSell = details.analystStrongSellCount ?: 0
    return "SB $strongBuy | Buy $buy | Hold $hold | Sell $sell | SS $strongSell"
}

private val FinancialDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun formatFinancialDate(epochSeconds: Long?): String =
    epochSeconds
        ?.let { Instant.ofEpochSecond(it).atZone(MarketClock.KENYA).format(FinancialDateFormat) + " EAT" }
        ?: "--"

private fun formatResultDate(details: StockDetails): String {
    val date = formatFinancialDate(details.nextEarningsEpochSeconds)
    val quarter = details.nextEarningsQuarterLabel
    return if (date != "--" && !quarter.isNullOrBlank()) "$date ($quarter)" else date
}

private fun formatPercentOne(value: Double?): String =
    value?.let { "%.1f%%".format(it) } ?: "--"

@Suppress("UNUSED_PARAMETER")
private fun formatSignedPercentHint(value: Double?, hint: String): String =
    value?.let { "%+.1f%%".format(it) } ?: "--"

@Suppress("UNUSED_PARAMETER")
private fun formatPercentHint(value: Double?, hint: String): String =
    value?.let { "%.1f%%".format(it) } ?: "--"

@Suppress("UNUSED_PARAMETER")
private fun formatRatioHint(value: Double?, hint: String): String =
    value?.let { "%.2f".format(it) } ?: "--"

@Suppress("UNUSED_PARAMETER")
private fun formatMultipleHint(value: Double?, hint: String): String =
    value?.takeIf { it > 0 }?.let { "%.1fx".format(it) } ?: "--"

private fun formatPlainNumber(value: Double?): String =
    value?.let { "%.2f".format(it) } ?: "--"

@Suppress("UNUSED_PARAMETER")
private fun formatPlainNumberHint(value: Double?, hint: String): String =
    value?.let { "%.2f".format(it) } ?: "--"

@Suppress("UNUSED_PARAMETER")
private fun formatCompactMoneyHint(value: Double?, hint: String): String =
    value?.let { formatCompactMoney(it) } ?: "--"

private fun formatCompactMoney(value: Double?): String {
    value ?: return "--"
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000_000_000.0 -> "$sign${"%.2f".format(abs / 1_000_000_000_000.0)}T"
        abs >= 1_000_000_000.0 -> "$sign${"%.2f".format(abs / 1_000_000_000.0)}B"
        abs >= 1_000_000.0 -> "$sign${"%.2f".format(abs / 1_000_000.0)}M"
        abs >= 1_000.0 -> "$sign${"%.1f".format(abs / 1_000.0)}K"
        else -> "$sign${"%.0f".format(abs)}"
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
        lots = state.lots,
        platformFeePercent = state.platformFeePercent
    )
    val lotPnlById = pnl.perLot.associateBy { it.lotId }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Your position",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (state.lots.isEmpty()) {
                            "Track your entry and invested amount."
                        } else {
                            "${state.lots.size} entr${if (state.lots.size == 1) "y" else "ies"} tracked"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAddLot,
                    modifier = Modifier.heightIn(min = 44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (state.lots.isEmpty()) "Add position" else "Add another")
                }
            }

            if (state.lots.isEmpty()) {
                PositionHintCard(
                    title = "What this unlocks",
                    body = "Net gain/loss, average entry, and fast take-profit or stop-loss alerts."
                )
            } else {
                if (state.platformFeePercent > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${"%.2f".format(state.platformFeePercent)}% fee deducted from returns",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    PositionMetricCard("Invested", formatPrice(pnl.totalInvested), Modifier.weight(1f))
                    Spacer(Modifier.size(8.dp))
                    PositionMetricCard("Value", formatPrice(pnl.positionValue), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    PositionMetricCard(
                        if (state.platformFeePercent > 0) "Net P&L" else "Total P&L",
                        formatSignedChange(pnl.totalPnl),
                        Modifier.weight(1f),
                        changeColor(pnl.totalPnl)
                    )
                    Spacer(Modifier.size(8.dp))
                    PositionMetricCard(
                        if (state.platformFeePercent > 0) "Net return" else "Return",
                        formatSignedPercent(pnl.percentPnl),
                        Modifier.weight(1f),
                        changeColor(pnl.percentPnl)
                    )
                }

                state.lots.forEachIndexed { index, lot ->
                    val lotTitle = if (lot.platform != null)
                        "Position ${index + 1} - ${lot.platform}"
                    else "Position ${index + 1}"
                    LotCard(
                        title = lotTitle,
                        lot = lot,
                        pnl = lotPnlById[lot.id],
                        platformFeePercent = state.platformFeePercent,
                        onEdit = { onEditLot(lot.id) },
                        onDelete = { onDeleteLot(lot.id) }
                    )
                }
                TpSlShortcuts(
                    hasEntryPrice = state.avgEntryPrice != null,
                    platformFeePercent = state.platformFeePercent,
                    onTakeProfit = onTakeProfit,
                    onStopLoss = onStopLoss
                )
            }
        }
    }
}

@Composable
private fun PositionHintCard(
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PositionMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier.heightIn(min = 70.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LotCard(
    title: String,
    lot: PositionLotEntity,
    pnl: com.stockwatchdog.app.domain.LotPnl?,
    platformFeePercent: Double,
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
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit position")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete position",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                PositionMetricCard("Entry", formatPrice(lot.entryPrice), Modifier.weight(1f))
                Spacer(Modifier.size(8.dp))
                PositionMetricCard("Invested", formatPrice(lot.amountInvested), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                PositionMetricCard(
                    if (platformFeePercent > 0) "Net P&L" else "P&L",
                    formatSignedChange(pnl?.pnl),
                    Modifier.weight(1f),
                    changeColor(pnl?.pnl)
                )
                Spacer(Modifier.size(8.dp))
                PositionMetricCard(
                    if (platformFeePercent > 0) "Net return" else "Return",
                    formatSignedPercent(pnl?.percentPnl),
                    Modifier.weight(1f),
                    changeColor(pnl?.percentPnl)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPositionDialog(
    symbol: String,
    companyName: String?,
    currentPrice: Double?,
    percentChange: Double?,
    currency: String?,
    entryPriceDraft: String,
    amountInvestedDraft: String,
    platformDraft: String,
    existingPlatforms: List<String>,
    isEditing: Boolean,
    onEntryChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onPlatformChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val entryOk = entryPriceDraft.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true
    val amountOk = amountInvestedDraft.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true
    val canSave = entryOk && amountOk

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 5.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isEditing) "Edit position" else "Add position",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Use the current price as your reference.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    PositionQuoteChip(
                        symbol = symbol,
                        companyName = companyName,
                        currentPrice = currentPrice,
                        percentChange = percentChange,
                        currency = currency
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "ENTRY DETAILS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            PositionDialogField(
                                value = entryPriceDraft,
                                onValueChange = onEntryChange,
                                label = "Entry price",
                                placeholder = currentPrice?.let { formatPrice(it, currency) } ?: "Price per share",
                                supporting = "The price you paid for one share.",
                                isError = entryPriceDraft.isNotBlank() && !entryOk
                            )
                            PositionDialogField(
                                value = amountInvestedDraft,
                                onValueChange = onAmountChange,
                                label = "Amount invested",
                                placeholder = "Total cash used",
                                supporting = "Your total money put into this entry.",
                                isError = amountInvestedDraft.isNotBlank() && !amountOk
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "PLATFORM",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    "Optional",
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            OutlinedTextField(
                                value = platformDraft,
                                onValueChange = onPlatformChange,
                                label = { Text("Broker or app") },
                                placeholder = { Text("e.g. Ndovu, Hisa") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (existingPlatforms.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    existingPlatforms.forEach { name ->
                                        AssistChip(
                                            onClick = { onPlatformChange(name) },
                                            label = { Text(name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PositionHintCard(
                        title = "Simple read",
                        body = "Entry price tells the app where you bought. Amount invested tells it how much money to track."
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = onSave,
                            enabled = canSave,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                        ) {
                            Text(if (isEditing) "Save position" else "Add position")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionQuoteChip(
    symbol: String,
    companyName: String?,
    currentPrice: Double?,
    percentChange: Double?,
    currency: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    symbol,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    companyName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatPrice(currentPrice, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${formatSignedPercent(percentChange)} today",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (percentChange == null) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    } else {
                        changeColor(percentChange)
                    }
                )
            }
        }
    }
}

@Composable
private fun PositionDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    supporting: String,
    isError: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = {
            Text(if (isError) "Enter a number above 0." else supporting)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TpSlShortcuts(
    hasEntryPrice: Boolean,
    platformFeePercent: Double,
    onTakeProfit: (Double) -> Unit,
    onStopLoss: (Double) -> Unit
) {
    if (!hasEntryPrice) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (platformFeePercent > 0) "Quick alerts (net after fees)" else "Quick alerts",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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
    AlertType.PERCENT_ABOVE_ENTRY -> "Net gain vs entry reaches +${"%.2f".format(a.threshold)}%"
    AlertType.PERCENT_BELOW_ENTRY -> "Net loss vs entry reaches -${"%.2f".format(a.threshold)}%"
    AlertType.EARNINGS_REMINDER -> {
        val days = a.threshold.toInt().coerceAtLeast(1)
        "Earnings reminder \u00b7 $days day${if (days == 1) "" else "s"} before"
    }
    AlertType.FIFTY_TWO_WEEK_HIGH -> "Touches a new 52-week high"
    AlertType.FIFTY_TWO_WEEK_LOW -> "Touches a new 52-week low"
    AlertType.MA200_CROSS_UP -> "Crosses above 200-day MA"
    AlertType.MA200_CROSS_DOWN -> "Crosses below 200-day MA"
    AlertType.VOLUME_SPIKE -> "Volume \u2265 ${"%.1f".format(a.threshold)}\u00d7 average"
    AlertType.ANALYST_TARGET_REACH -> "Reaches analyst price target"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CreateAlertDialog(
    type: AlertType,
    threshold: String,
    hasEntryPrice: Boolean,
    platformFeePercent: Double,
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
                Text("Trigger", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = type == AlertType.PRICE_ABOVE,
                        onClick = { onTypeChange(AlertType.PRICE_ABOVE) },
                        label = { Text("Price above") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    FilterChip(
                        selected = type == AlertType.PRICE_BELOW,
                        onClick = { onTypeChange(AlertType.PRICE_BELOW) },
                        label = { Text("Price below") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    FilterChip(
                        selected = type == AlertType.PERCENT_CHANGE_DAY,
                        onClick = { onTypeChange(AlertType.PERCENT_CHANGE_DAY) },
                        label = { Text("% day") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = type == AlertType.PERCENT_ABOVE_ENTRY,
                        onClick = { onTypeChange(AlertType.PERCENT_ABOVE_ENTRY) },
                        enabled = hasEntryPrice,
                        label = { Text(if (platformFeePercent > 0) "Net gain vs entry" else "Gain vs entry") },
                        modifier = Modifier.heightIn(min = 44.dp)
                    )
                    FilterChip(
                        selected = type == AlertType.PERCENT_BELOW_ENTRY,
                        onClick = { onTypeChange(AlertType.PERCENT_BELOW_ENTRY) },
                        enabled = hasEntryPrice,
                        label = { Text(if (platformFeePercent > 0) "Net loss vs entry" else "Loss vs entry") },
                        modifier = Modifier.heightIn(min = 44.dp)
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
                                    if (platformFeePercent > 0) "Net gain % (e.g. 10 for +10% after fees)" else "Gain % (e.g. 10 for +10%)"
                                AlertType.PERCENT_BELOW_ENTRY ->
                                    if (platformFeePercent > 0) "Net loss % (e.g. 5 for -5% after fees)" else "Loss % (e.g. 5 for -5%)"
                                AlertType.EARNINGS_REMINDER ->
                                    "Days before earnings (e.g. 3)"
                                AlertType.VOLUME_SPIKE ->
                                    "Ratio (e.g. 2.0 for 2× average)"
                                AlertType.FIFTY_TWO_WEEK_HIGH,
                                AlertType.FIFTY_TWO_WEEK_LOW,
                                AlertType.MA200_CROSS_UP,
                                AlertType.MA200_CROSS_DOWN,
                                AlertType.ANALYST_TARGET_REACH ->
                                    "Threshold (unused)"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (isPercent) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isEntryBased && platformFeePercent > 0)
                            "Use a positive number. Entry-based alerts use net return after fees. For \"Net loss vs entry 5%\" enter 5."
                        else
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
            ) { Text("Create alert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
