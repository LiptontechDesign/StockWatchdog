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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.domain.PositionCalculator
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.ui.components.FinancialResultBadge
import com.stockwatchdog.app.ui.components.FormHeader
import com.stockwatchdog.app.ui.components.FormSectionLabel
import com.stockwatchdog.app.ui.components.SearchResultRow
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
                    positionLotDao = container.database.positionLotDao(),
                    repo = container.marketDataRepository,
                    detailsRepo = container.stockDetailsRepository,
                    container = container
                )
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show undo snackbar after delete
    LaunchedEffect(state.undoDeleteEntity) {
        val entity = state.undoDeleteEntity ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "${entity.symbol} removed",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            vm.undoRemove()
        } else {
            vm.dismissUndoSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Watchlist", fontWeight = FontWeight.SemiBold)
                        state.marketSession?.let {
                            MarketSessionSubtitle(it)
                        } ?: state.marketSummaryText?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.openAddSheet() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add ticker")
                    }
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                EmptyWatchlist(onAdd = { vm.openAddSheet() })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(state.rows, key = { it.symbol }) { row ->
                        WatchRowItem(
                            row = row,
                            platformFeePercent = state.platformFeePercent,
                            onClick = { onOpenSymbol(row.symbol) },
                            onMoveUp = {
                                val i = items.indexOfFirst { it.symbol == row.symbol }
                                if (i > 0) vm.move(i, i - 1)
                            },
                            onMoveDown = {
                                val i = items.indexOfFirst { it.symbol == row.symbol }
                                if (i >= 0 && i < items.size - 1) vm.move(i, i + 1)
                            },
                            onRemove = { vm.confirmRemove(row.symbol) }
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

    // Confirm-delete dialog
    state.confirmDeleteSymbol?.let { sym ->
        AlertDialog(
            onDismissRequest = { vm.cancelRemove() },
            title = { Text("Remove $sym?") },
            text = { Text("This will remove the ticker, all its positions, and alerts. You can undo right after.") },
            confirmButton = {
                TextButton(onClick = { vm.remove(sym) }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { vm.cancelRemove() }) { Text("Cancel") }
            }
        )
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
private fun MarketSessionSubtitle(summary: MarketSessionSummary) {
    val statusColor = if (summary.isOpen) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val timeColor = MaterialTheme.colorScheme.secondary
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = statusColor,
                    fontWeight = FontWeight.ExtraBold
                )
            ) {
                append(summary.statusLabel)
            }
            append(" - ${summary.actionLabel} in ")
            withStyle(
                SpanStyle(
                    color = timeColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(summary.duration)
            }
            append(" - ")
            withStyle(SpanStyle(color = timeColor, fontWeight = FontWeight.SemiBold)) {
                append(summary.nairobiTime)
            }
            append(" Nairobi")
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun WatchRowItem(
    row: WatchRow,
    platformFeePercent: Double,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FinancialResultBadge(
                    details = row.details,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f, fill = false)
                )
            }
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
                    quantity = row.quantity,
                    platformFeePercent = platformFeePercent
                )
                val label = if (platformFeePercent > 0) "Net " else "vs entry "
                Text(
                    label + formatSignedPercent(pnl.percentPnl) +
                        (pnl.totalPnl?.let { "  (${formatSignedChange(it)})" } ?: ""),
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
                tint = MaterialTheme.colorScheme.error
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
        FormHeader(
            title = "Add ticker",
            subtitle = "Add one stock or ETF to your watchlist."
        )
        Spacer(Modifier.height(12.dp))
        FormSectionLabel("Stock")
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search symbol or company") },
            placeholder = { Text("AAPL, SPY, Nvidia") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (searching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Searching...", style = MaterialTheme.typography.bodySmall)
            } else if (query.isNotBlank()) {
                TextButton(
                    onClick = onAddManual,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text("Add ${query.trim().uppercase()} to watchlist")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(results, key = { it.symbol + (it.exchange ?: "") }) { m ->
                SearchResultRow(
                    symbol = m.symbol,
                    subtitle = listOfNotNull(m.name, m.exchange, m.type).joinToString(" | "),
                    onClick = { onPick(m) },
                    trailing = {
                        Icon(Icons.Default.Add, contentDescription = "Add ${m.symbol}")
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    thickness = 0.5.dp
                )
            }
        }
    }
}
