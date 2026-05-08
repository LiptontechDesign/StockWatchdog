package com.stockwatchdog.app.ui.dipfinder

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.domain.DipAnalysis
import com.stockwatchdog.app.domain.DipConfidence
import com.stockwatchdog.app.domain.DipLabel
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen

private val StrongDipColor = Color(0xFF2E7D32)
private val WatchDipColor = Color(0xFFF59E0B)
private val RiskyDipColor = Color(0xFFEA580C)
private val ValueTrapColor = NegativeRed
private val NotInDipColor = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DipFinderScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val vm: DipFinderViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DipFinderViewModel(repo = container.dipFinderRepository)
            }
        }
    )
    val state by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.consumeMessage()
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
                            Text("Dip Finder", fontWeight = FontWeight.SemiBold)
                            state.lastRefreshedAtMs?.let {
                                Text(
                                    "Updated ${relativeTime(it)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshAll(forceRefresh = true) }) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh all")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { IntroBanner() }

            item {
                SectionHeader(
                    icon = Icons.Default.Bolt,
                    title = "Auto Dip Finder",
                    subtitle = "Curated tickers we scan for you"
                )
            }
            if (state.autoRows.isEmpty()) {
                item { LoadingOrEmptyCard(isLoading = state.isRefreshing, label = "Scanning popular tickers…") }
            } else {
                items(state.autoRows, key = { "auto-${it.symbol}" }) { row ->
                    DipResultCard(
                        analysis = row,
                        showRemove = false,
                        onClick = { onOpenSymbol(row.symbol) },
                        onRefresh = { vm.refreshSymbol(row.symbol) },
                        onRemove = {}
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(
                    icon = Icons.Default.Star,
                    title = "My Watchlist",
                    subtitle = "Track any ticker you're curious about"
                )
            }
            item {
                AddTickerCard(
                    value = state.tickerInput,
                    isSubmitting = state.isAddingSymbol,
                    onValueChange = vm::onTickerInputChange,
                    onSubmit = vm::addCurrentInput
                )
            }
            if (state.watchlistRows.isEmpty() && state.watchlistSymbols.isEmpty()) {
                item { EmptyWatchlistHint() }
            } else if (state.watchlistRows.isEmpty()) {
                item { LoadingOrEmptyCard(isLoading = true, label = "Analysing your tickers…") }
            } else {
                items(state.watchlistRows, key = { "watch-${it.symbol}" }) { row ->
                    DipResultCard(
                        analysis = row,
                        showRemove = true,
                        onClick = { onOpenSymbol(row.symbol) },
                        onRefresh = { vm.refreshSymbol(row.symbol) },
                        onRemove = { vm.removeFromWatchlist(row.symbol) }
                    )
                }
            }

            item { DisclaimerFooter() }
        }
    }
}

// ── Top of screen ───────────────────────────────────────────────────────

@Composable
private fun IntroBanner() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Find healthy dips, in plain English",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "We look at the price drop and the company's basic health, then explain what it means.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Add ticker card ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTickerCard(
    value: String,
    isSubmitting: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Add a ticker (e.g. AAPL)") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSubmit,
                enabled = value.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = "Add to watchlist")
                }
            }
        }
    }
}

// ── Result card ─────────────────────────────────────────────────────────

@Composable
private fun DipResultCard(
    analysis: DipAnalysis,
    showRemove: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit
) {
    val labelColor = labelColor(analysis.label)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: ticker + label badge + price
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(labelColor)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        analysis.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    analysis.name?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(analysis.currentPrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    analysis.pctFromHigh?.let {
                        if (it > 0) {
                            Text(
                                "↓ ${"%.0f".format(it)}% from high",
                                style = MaterialTheme.typography.labelSmall,
                                color = NegativeRed
                            )
                        }
                    }
                }
            }

            // Score + label + confidence row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreBadge(score = analysis.score, color = labelColor)
                Column(Modifier.weight(1f)) {
                    Text(
                        analysis.label.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = labelColor
                    )
                    Text(
                        analysis.label.tagline,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ConfidenceChip(confidence = analysis.confidence)
            }

            // Optional near-low pill
            if (analysis.nearLow) {
                AssistChip(
                    onClick = {},
                    label = { Text("Near 52-week low") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = labelColor
                    )
                )
            }

            // Plain-English reason
            Text(
                analysis.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Footer: last updated + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Updated ${relativeTime(analysis.computedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh ${analysis.symbol}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove ${analysis.symbol}",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Int, color: Color) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                "score",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ConfidenceChip(confidence: DipConfidence) {
    val color = when (confidence) {
        DipConfidence.HIGH -> PositiveGreen
        DipConfidence.MEDIUM -> Color(0xFFF59E0B)
        DipConfidence.LOW -> NotInDipColor
    }
    Text(
        confidence.displayName,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// ── Helpers / placeholders ─────────────────────────────────────────────

@Composable
private fun LoadingOrEmptyCard(isLoading: Boolean, label: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyWatchlistHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Type a ticker above to add it to your watchlist. We'll analyse it and explain whether it looks like a healthy dip.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DisclaimerFooter() {
    Text(
        "Educational tool only. Not buy/sell advice. Free APIs may be incomplete or delayed.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 4.dp, end = 4.dp)
    )
}

// ── Pure helpers ───────────────────────────────────────────────────────

private fun labelColor(label: DipLabel): Color = when (label) {
    DipLabel.STRONG_DIP -> StrongDipColor
    DipLabel.WATCH_DIP -> WatchDipColor
    DipLabel.RISKY_DIP -> RiskyDipColor
    DipLabel.VALUE_TRAP -> ValueTrapColor
    DipLabel.NOT_IN_DIP -> NotInDipColor
}

/** "5m ago", "2h ago", "3d ago" — keeps the chrome lightweight. */
private fun relativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

