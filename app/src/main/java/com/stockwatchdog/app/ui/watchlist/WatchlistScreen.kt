package com.stockwatchdog.app.ui.watchlist

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.domain.PositionCalculator
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.ui.components.changeColor
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.components.formatSignedChange
import com.stockwatchdog.app.ui.components.formatSignedPercent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val vm: WatchlistViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WatchlistViewModel(
                    dao = container.database.watchlistDao(),
                    repo = container.marketDataRepository,
                    container = container
                )
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Watchlist", fontWeight = FontWeight.SemiBold)
                        state.marketSummaryText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(force = true) }) {
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
                onClick = { vm.openAddSheet() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add ticker") }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                EmptyWatchlist(onAdd = { vm.openAddSheet() })
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.symbol }) { row ->
                        WatchRowItem(
                            row = row,
                            onClick = { onOpenSymbol(row.symbol) },
                            onMoveUp = {
                                val i = items.indexOfFirst { it.symbol == row.symbol }
                                if (i > 0) vm.move(i, i - 1)
                            },
                            onMoveDown = {
                                val i = items.indexOfFirst { it.symbol == row.symbol }
                                if (i >= 0 && i < items.size - 1) vm.move(i, i + 1)
                            },
                            onRemove = { vm.remove(row.symbol) }
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

    if (state.addSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { vm.closeAddSheet() },
            sheetState = sheetState
        ) {
            AddTickerSheet(
                query = state.addQuery,
                searching = state.searching,
                results = state.searchResults,
                onQueryChange = vm::onAddQueryChange,
                onAddManual = { vm.addSymbol(state.addQuery) },
                onPick = { vm.addSymbol(it.symbol, it.name) }
            )
        }
    }
}

@Composable
private fun WatchRowItem(
    row: WatchRow,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.symbol,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val subtitle = row.name ?: row.quote?.name ?: row.error
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (row.entryPrice != null && row.quote != null) {
                val pnl = PositionCalculator.calculate(
                    currentPrice = row.quote.price,
                    entryPrice = row.entryPrice,
                    quantity = row.quantity
                )
                Text(
                    "vs entry ${formatSignedPercent(pnl.percentPnl)}" +
                        (pnl.totalPnl?.let { " • ${formatSignedChange(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = changeColor(pnl.percentPnl),
                    maxLines = 1
                )
            }
        }
        PriceColumn(row.quote, hasError = row.error != null)
        Column {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PriceColumn(quote: Quote?, hasError: Boolean) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            formatPrice(quote?.price, quote?.currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (hasError && quote == null)
                MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
        val pct = quote?.percentChange
        val chg = quote?.change
        Text(
            "${formatSignedChange(chg)} (${formatSignedPercent(pct)})",
            style = MaterialTheme.typography.labelSmall,
            color = changeColor(pct)
        )
    }
}

@Composable
private fun EmptyWatchlist(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "No tickers yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a stock or ETF to start watching.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Add ticker")
        }
    }
}

@Composable
private fun AddTickerSheet(
    query: String,
    searching: Boolean,
    results: List<com.stockwatchdog.app.domain.SymbolMatch>,
    onQueryChange: (String) -> Unit,
    onAddManual: () -> Unit,
    onPick: (com.stockwatchdog.app.domain.SymbolMatch) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .heightIn(min = 200.dp, max = 520.dp)
    ) {
        Text(
            "Add ticker",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Symbol or company (e.g. AAPL, SPY)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Searching…", style = MaterialTheme.typography.bodySmall)
            } else if (query.isNotBlank()) {
                TextButton(onClick = onAddManual) {
                    Text("Add \"${query.trim().uppercase()}\" directly")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(results, key = { it.symbol + (it.exchange ?: "") }) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(m) }
                        .padding(vertical = 10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.symbol, fontWeight = FontWeight.Medium)
                        val sub = listOfNotNull(m.name, m.exchange, m.type)
                            .joinToString(" • ")
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}
