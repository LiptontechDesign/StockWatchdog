package com.stockwatchdog.app.ui.dip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.domain.SymbolMatch
import com.stockwatchdog.app.ui.components.formatPrice

/**
 * Add/edit dialog for a Dip Tracker setup. Reuses the same symbol search +
 * "set buy zone X% below current price" presets the old screen had, in a
 * cleaner card-style layout.
 */
@Composable
internal fun DipAddEditDialog(
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
    val canSave = symbol.isNotBlank() && low != null && high != null &&
        low > 0 && high > 0 && !zoneError && !strongError

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isEditing) "Edit dip setup" else "Add dip setup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (selectedSymbol == null && !isEditing) {
                    // Search & pick a symbol.
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search ticker or company") },
                        placeholder = { Text("e.g. AAPL, Apple, SPY") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    if (isSearching) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Searching…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (searchResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            items(searchResults, key = { it.symbol + (it.exchange ?: "") }) { m ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectSymbol(m) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(m.symbol, fontWeight = FontWeight.SemiBold)
                                        val sub = listOfNotNull(m.name, m.exchange, m.type)
                                            .joinToString(" • ")
                                        if (sub.isNotBlank()) Text(
                                            sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        TextButton(onClick = { onSymbolChange(searchQuery.trim().uppercase()) }) {
                            Text("Use \"${searchQuery.trim().uppercase()}\" directly")
                        }
                    }
                } else {
                    // Selected-symbol pill (with quote preview & clear button).
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    selectedSymbol?.symbol ?: symbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val sub = selectedSymbol?.name
                                    ?: selectedQuote?.name
                                if (!sub.isNullOrBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (selectedQuote != null) {
                                    Text(
                                        "Last: ${formatPrice(selectedQuote.price, selectedQuote.currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isEditing) {
                                IconButton(onClick = onClearSymbol) {
                                    Icon(Icons.Default.Close, contentDescription = "Change symbol")
                                }
                            }
                        }
                    }
                }

                // ── Buy zone presets ────────────────────────────────────
                if (selectedQuote != null) {
                    Text(
                        "Quick presets (based on current price)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5.0, 10.0, 15.0, 20.0).forEach { p ->
                            AssistChip(
                                onClick = { onApplyPresetBuyZone(p) },
                                label = { Text("-${p.toInt()}%") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = buyZoneLow,
                    onValueChange = onBuyZoneLowChange,
                    label = { Text("Buy zone low") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = zoneError
                )
                OutlinedTextField(
                    value = buyZoneHigh,
                    onValueChange = onBuyZoneHighChange,
                    label = { Text("Buy zone high") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = zoneError,
                    supportingText = if (zoneError) {
                        { Text("High must be greater than low") }
                    } else null
                )
                OutlinedTextField(
                    value = strongBuy,
                    onValueChange = onStrongBuyChange,
                    label = { Text("Strong-buy below (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = strongError,
                    supportingText = if (strongError) {
                        { Text("Strong-buy must be ≤ buy zone low") }
                    } else null
                )

                if (low != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5.0, 10.0, 15.0).forEach { p ->
                            AssistChip(
                                onClick = { onApplyPresetStrongBuy(p) },
                                label = { Text("Strong -${p.toInt()}%") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
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
