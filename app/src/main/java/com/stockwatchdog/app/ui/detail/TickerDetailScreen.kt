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
import com.stockwatchdog.app.data.api.StockDetails
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
import com.stockwatchdog.app.util.MarketClock
import java.time.Instant
import java.time.format.DateTimeFormatter

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
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        val tabTitles = listOf("Position", "Alerts", "Financials")

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
        CreateAlertDialog(
            type = state.newAlertType,
            threshold = state.newAlertThreshold,
            hasEntryPrice = state.avgEntryPrice != null,
            platformFeePercent = state.platformFeePercent,
            onTypeChange = vm::onAlertTypeChange,
            onThresholdChange = vm::onAlertThresholdChange,
            onSave = { vm.saveAlert() },
            onDismiss = { vm.closeCreateAlert() }
        )
    }

    if (state.lotDialogOpen) {
        val existingPlatforms = state.lots.mapNotNull { it.platform }.distinct()
        EditPositionDialog(
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

        FinancialReadCard(details = details, currentPrice = state.quote?.price)
        FinancialMeaningCard(details = details)

        FinancialMetricCard("Results") {
            FinancialMetricRow(
                "Next results",
                formatFinancialDate(details.nextEarningsEpochSeconds),
                "Last EPS",
                formatEpsSurprise(details)
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                "Revenue surprise",
                "--",
                "Guidance",
                "--"
            )
        }

        FinancialMetricCard("Growth") {
            FinancialMetricRow(
                "Revenue growth",
                formatSignedPercentOne(details.revenueGrowthPct),
                "EPS growth",
                formatSignedPercentOne(details.epsGrowthPct),
                value1Positive = details.revenueGrowthPct,
                value2Positive = details.epsGrowthPct
            )
        }

        FinancialMetricCard("Profitability") {
            FinancialMetricRow(
                "Revenue",
                formatCompactMoney(details.totalRevenue),
                "Profit margin",
                formatPercentOne(details.profitMarginPct),
                value2Positive = details.profitMarginPct
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                "Operating margin",
                formatPercentOne(details.operatingMarginPct),
                "Free cash flow",
                formatCompactMoney(details.freeCashflow),
                value1Positive = details.operatingMarginPct,
                value2Positive = details.freeCashflow
            )
        }

        FinancialMetricCard("Safety") {
            FinancialMetricRow(
                "Debt / equity",
                formatRatio(details.debtToEquity),
                "Total debt",
                formatCompactMoney(details.totalDebt)
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                "Cash",
                formatCompactMoney(details.totalCash),
                "Current ratio",
                formatRatio(details.currentRatio)
            )
        }

        FinancialMetricCard("Valuation") {
            FinancialMetricRow(
                "P/E",
                formatMultiple(details.trailingPe),
                "Forward P/E",
                formatMultiple(details.forwardPe)
            )
            Spacer(Modifier.height(8.dp))
            FinancialMetricRow(
                "EPS",
                formatPlainNumber(details.epsTtm),
                "Analyst target",
                formatPrice(details.analystTargetMean)
            )
        }
    }
}

@Composable
private fun FinancialReadCard(details: StockDetails, currentPrice: Double?) {
    val notes = buildFinancialRead(details, currentPrice)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Simple read", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                notes.ifBlank { "Not enough free fundamental data yet for a clean read." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinancialMeaningCard(details: StockDetails) {
    val lines = buildFinancialMeaning(details)
    if (lines.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Plain meaning", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
    value1Positive: Double? = null,
    value2Positive: Double? = null
) {
    Row(Modifier.fillMaxWidth()) {
        FinancialMetricCell(label1, value1, value1Positive, Modifier.weight(1f))
        Spacer(Modifier.size(16.dp))
        FinancialMetricCell(label2, value2, value2Positive, Modifier.weight(1f))
    }
}

@Composable
private fun FinancialMetricCell(
    label: String,
    value: String,
    signedTone: Double?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = signedTone?.let { changeColor(it) } ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun buildFinancialRead(details: StockDetails, currentPrice: Double?): String {
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
    return parts.joinToString(separator = ", ").replaceFirstChar { it.uppercaseChar() }
}

private fun buildFinancialMeaning(details: StockDetails): List<String> {
    val lines = mutableListOf<String>()
    details.revenueGrowthPct?.let {
        lines += if (it >= 0) {
            "Revenue growth: sales are increasing, which is usually a healthy sign."
        } else {
            "Revenue growth: sales are falling, so demand or pricing may be under pressure."
        }
    }
    details.epsGrowthPct?.let {
        lines += if (it >= 0) {
            "EPS growth: profit per share is improving; stock prices often follow this over time."
        } else {
            "EPS growth: profit per share is weakening, which can pressure the stock."
        }
    }
    details.profitMarginPct?.let {
        lines += when {
            it >= 20.0 -> "Profit margin: the company keeps a strong share of sales as profit."
            it > 0.0 -> "Profit margin: the company is profitable, but check whether margins are improving."
            else -> "Profit margin: the company is not keeping profit from sales right now."
        }
    }
    details.debtToEquity?.let {
        lines += if (it <= 100.0) {
            "Debt: borrowing looks manageable compared with shareholder equity."
        } else {
            "Debt: borrowing is elevated, so interest costs and risk matter more."
        }
    }
    details.trailingPe?.let {
        lines += when {
            it <= 0.0 -> "P/E: valuation is unclear because earnings are negative or unavailable."
            it < 18.0 -> "P/E: the stock looks modestly priced compared with current profit."
            it <= 35.0 -> "P/E: investors are paying a reasonable-to-premium price for profit."
            else -> "P/E: the stock is expensive unless profit grows strongly."
        }
    }
    if (lines.isEmpty() && details.nextEarningsEpochSeconds != null) {
        lines += "Results date: watch the next report because fresh numbers can change the buy case."
    }
    return lines.take(5)
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

private val FinancialDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun formatFinancialDate(epochSeconds: Long?): String =
    epochSeconds
        ?.let { Instant.ofEpochSecond(it).atZone(MarketClock.KENYA).format(FinancialDateFormat) + " EAT" }
        ?: "--"

private fun formatSignedPercentOne(value: Double?): String =
    value?.let { "%+.1f%%".format(it) } ?: "--"

private fun formatPercentOne(value: Double?): String =
    value?.let { "%.1f%%".format(it) } ?: "--"

private fun formatRatio(value: Double?): String =
    value?.let { "%.2f".format(it) } ?: "--"

private fun formatMultiple(value: Double?): String =
    value?.takeIf { it > 0 }?.let { "%.1fx".format(it) } ?: "--"

private fun formatPlainNumber(value: Double?): String =
    value?.let { "%.2f".format(it) } ?: "--"

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
                if (state.platformFeePercent > 0) {
                    Text(
                        "${"%.2f".format(state.platformFeePercent)}% fee deducted from returns",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SummaryRow(
                    "Total invested", formatPrice(pnl.totalInvested),
                    "Current value", formatPrice(pnl.positionValue)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.platformFeePercent > 0) "Net P&L" else "Total P&L",
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
                            if (state.platformFeePercent > 0) "Net return" else "Return",
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
                    val lotTitle = if (lot.platform != null)
                        "Position ${index + 1} · ${lot.platform}"
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
                Spacer(Modifier.height(12.dp))
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
                        if (platformFeePercent > 0) "Net since entry" else "Since entry",
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
                        if (platformFeePercent > 0) "Net return" else "Return",
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = platformDraft,
                    onValueChange = onPlatformChange,
                    label = { Text("Platform / broker (optional)") },
                    placeholder = { Text("e.g. Ndovu, Hisa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existingPlatforms.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingPlatforms.forEach { name ->
                            AssistChip(
                                onClick = { onPlatformChange(name) },
                                label = { Text(name) }
                            )
                        }
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
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
                        label = { Text(if (platformFeePercent > 0) "Net gain vs entry" else "Gain vs entry") }
                    )
                    Spacer(Modifier.size(6.dp))
                    FilterChip(
                        selected = type == AlertType.PERCENT_BELOW_ENTRY,
                        onClick = { onTypeChange(AlertType.PERCENT_BELOW_ENTRY) },
                        enabled = hasEntryPrice,
                        label = { Text(if (platformFeePercent > 0) "Net loss vs entry" else "Loss vs entry") }
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
