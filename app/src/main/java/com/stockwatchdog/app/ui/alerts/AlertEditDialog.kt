package com.stockwatchdog.app.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stockwatchdog.app.data.db.entities.AlertType

/**
 * Single dialog used for both **create** and **edit** of any alert type.
 *
 * The form adapts to the selected type:
 *  - Price types  → numeric input labelled "Price"
 *  - Percent type → numeric input labelled "Percent"
 *  - Earnings     → integer days-before
 *  - Volume spike → ratio (e.g. 2.0)
 *  - 52w / MA200 / target → no threshold input
 */
@Composable
internal fun AlertEditDialog(
    isEditing: Boolean,
    symbolLocked: Boolean,
    symbol: String,
    type: AlertType,
    threshold: String,
    notes: String,
    autoDisable: Boolean,
    marketHoursOnly: Boolean?,
    hasEntryPrice: Boolean,
    onSymbolChange: (String) -> Unit,
    onTypeChange: (AlertType) -> Unit,
    onThresholdChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onAutoDisableChange: (Boolean) -> Unit,
    onMarketHoursOnlyChange: (Boolean?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val needsThreshold = when (type) {
        AlertType.FIFTY_TWO_WEEK_HIGH,
        AlertType.FIFTY_TWO_WEEK_LOW,
        AlertType.MA200_CROSS_UP,
        AlertType.MA200_CROSS_DOWN,
        AlertType.ANALYST_TARGET_REACH -> false
        else -> true
    }
    val canSave = symbol.isNotBlank() && (
        !needsThreshold ||
            (threshold.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true)
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isEditing) "Edit alert" else "New alert",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Symbol — locked when opened from a specific ticker.
                OutlinedTextField(
                    value = symbol,
                    onValueChange = onSymbolChange,
                    label = { Text("Symbol") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !symbolLocked,
                    singleLine = true
                )

                // Type chips — grouped by family.
                SectionLabel("Trigger")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipRow(
                        items = listOf(
                            AlertType.PRICE_ABOVE to "Price above",
                            AlertType.PRICE_BELOW to "Price below",
                            AlertType.PERCENT_CHANGE_DAY to "% day move"
                        ),
                        selected = type,
                        onSelect = onTypeChange
                    )
                    ChipRow(
                        items = listOfNotNull(
                            (AlertType.PERCENT_ABOVE_ENTRY to "Gain vs entry").takeIf { hasEntryPrice },
                            (AlertType.PERCENT_BELOW_ENTRY to "Loss vs entry").takeIf { hasEntryPrice }
                        ),
                        selected = type,
                        onSelect = onTypeChange
                    )
                    ChipRow(
                        items = listOf(
                            AlertType.FIFTY_TWO_WEEK_HIGH to "52w high",
                            AlertType.FIFTY_TWO_WEEK_LOW to "52w low",
                            AlertType.MA200_CROSS_UP to "MA200 \u2191"
                        ),
                        selected = type,
                        onSelect = onTypeChange
                    )
                    ChipRow(
                        items = listOf(
                            AlertType.MA200_CROSS_DOWN to "MA200 \u2193",
                            AlertType.EARNINGS_REMINDER to "Earnings",
                            AlertType.VOLUME_SPIKE to "Volume spike"
                        ),
                        selected = type,
                        onSelect = onTypeChange
                    )
                    ChipRow(
                        items = listOf(AlertType.ANALYST_TARGET_REACH to "Analyst target"),
                        selected = type,
                        onSelect = onTypeChange
                    )
                }

                if (needsThreshold) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = onThresholdChange,
                        label = { Text(thresholdLabel(type)) },
                        supportingText = thresholdHint(type)?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Note (optional)") },
                    placeholder = { Text("Why did you set this? Shows in the notification.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                ToggleRow(
                    title = "Auto-disable after firing",
                    subtitle = "Use for take-profit / stop-loss style one-shot alerts.",
                    checked = autoDisable,
                    onCheckedChange = onAutoDisableChange
                )
                ToggleRow(
                    title = "Only while NYSE is open",
                    subtitle = if (marketHoursOnly == null)
                        "Use the global setting from Settings."
                    else if (marketHoursOnly)
                        "Quiet outside US market hours."
                    else "Always notify (overrides global).",
                    checked = marketHoursOnly == true,
                    onCheckedChange = { v -> onMarketHoursOnlyChange(if (v) true else null) }
                )

                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = canSave) {
                        Text(if (isEditing) "Save" else "Create")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(
    items: List<Pair<AlertType, String>>,
    selected: AlertType,
    onSelect: (AlertType) -> Unit
) {
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (t, label) ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(t) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun thresholdLabel(t: AlertType): String = when (t) {
    AlertType.PRICE_ABOVE, AlertType.PRICE_BELOW -> "Price"
    AlertType.PERCENT_CHANGE_DAY -> "Percent"
    AlertType.PERCENT_ABOVE_ENTRY -> "Gain %"
    AlertType.PERCENT_BELOW_ENTRY -> "Loss %"
    AlertType.EARNINGS_REMINDER -> "Days before"
    AlertType.VOLUME_SPIKE -> "Ratio (e.g. 2.0 = 2\u00d7 avg)"
    else -> ""
}

private fun thresholdHint(t: AlertType): String? = when (t) {
    AlertType.PERCENT_CHANGE_DAY -> "Fires once per day when |today's change| reaches this %."
    AlertType.PERCENT_ABOVE_ENTRY -> "Use a positive number. \"+10\" = take profit at +10%."
    AlertType.PERCENT_BELOW_ENTRY -> "Use a positive number. \"5\" = stop loss at -5%."
    AlertType.EARNINGS_REMINDER -> "Fires once per day while earnings is within this many days."
    AlertType.VOLUME_SPIKE -> "Fires once per day when volume exceeds this multiple of the 90d average."
    else -> null
}
