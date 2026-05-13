package com.stockwatchdog.app.ui.portfolio

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.components.changeColor
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.components.formatSignedChange
import com.stockwatchdog.app.ui.components.formatSignedPercent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val vm: PortfolioViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PortfolioViewModel(
                    watchlistDao = container.database.watchlistDao(),
                    positionLotDao = container.database.positionLotDao(),
                    repo = container.marketDataRepository,
                    settingsRepository = container.settingsRepository
                )
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portfolio", fontWeight = FontWeight.SemiBold) },
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
        }
    ) { padding ->
        if (state.isRefreshing && state.holdings.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.holdings.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("No positions yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add positions to your watchlist tickers to see your portfolio summary here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding)
            ) {
                item {
                    PortfolioSummaryCard(state)
                }
                items(state.holdings, key = { "${it.symbol}::${it.platform ?: ""}" }) { holding ->
                    HoldingRow(
                        holding = holding,
                        platformFeePercent = state.platformFeePercent,
                        onClick = { onOpenSymbol(holding.symbol) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioSummaryCard(state: PortfolioUiState) {
    val brokerBreakdowns = state.brokerBreakdowns()
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
            val valueText = if (state.totalValue != null) {
                formatPrice(state.totalValue)
            } else {
                formatPrice(state.totalInvested)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.weight(1.15f)) {
                    Text(
                        "Total portfolio",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        valueText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.totalPnl != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatSignedChange(state.totalPnl),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = changeColor(state.totalPnl)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "(${formatSignedPercent(state.totalPercentPnl)})",
                                style = MaterialTheme.typography.titleSmall,
                                color = changeColor(state.totalPercentPnl)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(0.85f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    SummaryMetric(
                        label = "Invested",
                        value = formatPrice(state.totalInvested),
                        horizontalAlignment = Alignment.End
                    )
                    SummaryMetric(
                        label = "Current",
                        value = if (state.totalValue != null) formatPrice(state.totalValue) else "--",
                        horizontalAlignment = Alignment.End
                    )
                    SummaryMetric(
                        label = "Net return",
                        value = state.totalPercentPnl?.let { formatSignedPercent(it) } ?: "--",
                        color = state.totalPercentPnl?.let { changeColor(it) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        horizontalAlignment = Alignment.End
                    )
                }
            }

            if (state.platformFeePercent > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${"%.2f".format(state.platformFeePercent)}% fee deducted from returns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (brokerBreakdowns.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Broker performance",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                BrokerBreakdownGrid(
                    brokers = brokerBreakdowns,
                    totalInvested = state.totalInvested
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "${state.holdings.size} holding${if (state.holdings.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(horizontalAlignment = horizontalAlignment) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun BrokerBreakdownGrid(
    brokers: List<BrokerBreakdown>,
    totalInvested: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        brokers.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { broker ->
                    BrokerBreakdownRow(
                        broker = broker,
                        totalInvested = totalInvested,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1 && brokers.size > 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class BrokerBreakdown(
    val name: String,
    val invested: Double,
    val currentValue: Double?,
    val pnl: Double?,
    val percentPnl: Double?,
    val holdingsCount: Int
)

private fun PortfolioUiState.brokerBreakdowns(): List<BrokerBreakdown> =
    holdings
        .groupBy { holding ->
            holding.platform
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "No broker"
        }
        .map { (platform, rows) ->
            val invested = rows.sumOf { it.totalInvested }
            val hasCurrentValues = rows.all { it.currentValue != null && it.pnl != null }
            val pnl = if (hasCurrentValues) rows.sumOf { it.pnl ?: 0.0 } else null
            BrokerBreakdown(
                name = platform,
                invested = invested,
                currentValue = if (hasCurrentValues) rows.sumOf { it.currentValue ?: 0.0 } else null,
                pnl = pnl,
                percentPnl = if (pnl != null && invested > 0.0) pnl / invested * 100.0 else null,
                holdingsCount = rows.size
            )
        }
        .sortedByDescending { it.invested }

@Composable
private fun BrokerBreakdownRow(
    broker: BrokerBreakdown,
    totalInvested: Double,
    modifier: Modifier = Modifier
) {
    val share = if (totalInvested > 0.0) {
        (broker.invested / totalInvested).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val performanceColor = broker.percentPnl?.let { changeColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                broker.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${broker.holdingsCount} holding${if (broker.holdingsCount != 1) "s" else ""} - ${"%.0f".format(share * 100f)}% of invested",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share)
                        .height(5.dp)
                        .background(performanceColor.copy(alpha = 0.80f))
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BrokerMetric(
                    label = "Invested",
                    value = formatPrice(broker.invested),
                    modifier = Modifier.weight(1f),
                )
                BrokerMetric(
                    label = "Current",
                    value = formatPrice(broker.currentValue ?: broker.invested),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BrokerMetric(
                    label = "Profit/loss",
                    value = broker.pnl?.let { formatSignedChange(it) } ?: "--",
                    color = performanceColor,
                    modifier = Modifier.weight(1f),
                )
                BrokerMetric(
                    label = "Net return",
                    value = broker.percentPnl?.let { formatSignedPercent(it) } ?: "--",
                    color = performanceColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BrokerMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HoldingRow(
    holding: HoldingRow,
    platformFeePercent: Double,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                holding.symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            holding.name?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            holding.platform?.let { platform ->
                Text(
                    platform,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Invested: ${formatPrice(holding.totalInvested)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (holding.currentValue != null) {
                Text(
                    formatPrice(holding.currentValue),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    "--",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (holding.pnl != null) {
                val prefix = if (platformFeePercent > 0) "Net " else ""
                Text(
                    prefix + formatSignedPercent(holding.percentPnl) +
                        "  (${formatSignedChange(holding.pnl)})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = changeColor(holding.percentPnl)
                )
            }
        }
    }
}
