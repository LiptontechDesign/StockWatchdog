package com.stockwatchdog.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stockwatchdog.app.data.db.entities.AlertType
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.components.formatSignedPercent

private val SheetBg = Color(0xFFF0EFF7)
private val ScreenDark = Color(0xFF1A1830)
private val TextDark = Color(0xFF0D0B1E)
private val Navy = Color(0xFF1E1852)
private val NavyMid = Color(0xFF3D3894)
private val BorderLavender = Color(0xFFB0AEC8)
private val CardBorder = Color(0xFFC8C5E8)
private val PriceFill = Color(0xFFEDEAFB)
private val SubText = Color(0xFF2D2860)
private val Warning = Color(0xFFC47A00)
private val Success = Color(0xFF0A4A28)
private val SuccessBg = Color(0xFFC6EFD8)
private val WarningBg = Color(0xFFFDE8C0)

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
    companyName: String? = null,
    currentPrice: Double? = null,
    percentChange: Double? = null,
    currency: String? = null,
    onSymbolChange: (String) -> Unit,
    onTypeChange: (AlertType) -> Unit,
    onThresholdChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onAutoDisableChange: (Boolean) -> Unit,
    onMarketHoursOnlyChange: (Boolean?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val needsThreshold = type.needsThreshold()
    val thresholdNumber = threshold.replace(",", ".").toDoubleOrNull()
    val canSave = symbol.isNotBlank() && (
        !needsThreshold || thresholdNumber?.let { it > 0 } == true
        )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SheetHandle()
                Text(
                    if (isEditing) "Edit alert" else "Add alert",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (symbolLocked || symbol.isNotBlank()) {
                    AlertStockChip(
                        symbol = symbol.ifBlank { "Ticker" },
                        subtitle = if (symbolLocked) "Tracking this stock" else "Alert stock",
                        companyName = companyName,
                        currentPrice = currentPrice,
                        percentChange = percentChange,
                        currency = currency,
                        onClear = null
                    )
                } else {
                    TickerInputCard(symbol = symbol, onSymbolChange = onSymbolChange)
                }

                TriggerPicker(
                    type = type,
                    hasEntryPrice = hasEntryPrice,
                    onTypeChange = onTypeChange
                )

                if (needsThreshold) {
                    TriggerValueCard(
                        type = type,
                        threshold = threshold,
                        onThresholdChange = onThresholdChange
                    )
                } else {
                    AutomaticTriggerCard(type)
                }

                AlertMeaningCard(
                    symbol = symbol,
                    type = type,
                    threshold = threshold,
                    marketHoursOnly = marketHoursOnly
                )

                TimingCard(
                    autoDisable = autoDisable,
                    marketHoursOnly = marketHoursOnly,
                    onAutoDisableChange = onAutoDisableChange,
                    onMarketHoursOnlyChange = onMarketHoursOnlyChange
                )

                NotesCard(notes = notes, onNotesChange = onNotesChange)

                FormActions(
                    primaryText = if (isEditing) "Save alert" else "Create alert",
                    enabled = canSave,
                    onPrimary = onSave,
                    onCancel = onDismiss
                )
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
    )
}

@Composable
private fun AlertStockChip(
    symbol: String,
    subtitle: String,
    companyName: String?,
    currentPrice: Double?,
    percentChange: Double?,
    currency: String?,
    onClear: (() -> Unit)?
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val muted = onContainer.copy(alpha = 0.74f)
    val changeColor = when {
        percentChange == null -> muted
        percentChange >= 0.0 -> Color(0xFF10B981)
        else -> Color(0xFFFF6B6B)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(12.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                symbol.uppercase(),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                companyName?.takeIf { it.isNotBlank() } ?: subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (currentPrice != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatPrice(currentPrice, currency),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = onContainer
                    )
                    if (percentChange != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (percentChange >= 0.0) "▲" else "▼"} ${formatSignedPercent(percentChange)} today",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = changeColor
                        )
                    }
                }
            }
        }
        if (onClear != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(36.dp)
                    .background(onContainer.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Clear stock", tint = onContainer)
            }
        }
    }
}

@Composable
private fun TickerInputCard(
    symbol: String,
    onSymbolChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel("Alert stock")
        FilledInput(
            value = symbol,
            onValueChange = onSymbolChange,
            placeholder = "Ticker",
            keyboardType = KeyboardType.Text
        )
    }
}

@Composable
private fun TriggerPicker(
    type: AlertType,
    hasEntryPrice: Boolean,
    onTypeChange: (AlertType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Quick presets - trigger")
        TriggerRow(
            items = listOf(
                AlertType.PRICE_ABOVE to "Price above",
                AlertType.PRICE_BELOW to "Price below",
                AlertType.PERCENT_CHANGE_DAY to "Day %"
            ),
            selected = type,
            onSelect = onTypeChange
        )
        TriggerRow(
            items = listOfNotNull(
                (AlertType.PERCENT_ABOVE_ENTRY to "Gain vs entry").takeIf { hasEntryPrice },
                (AlertType.PERCENT_BELOW_ENTRY to "Loss vs entry").takeIf { hasEntryPrice },
                AlertType.EARNINGS_REMINDER to "Results"
            ),
            selected = type,
            onSelect = onTypeChange
        )
        TriggerRow(
            items = listOf(
                AlertType.FIFTY_TWO_WEEK_HIGH to "52w high",
                AlertType.FIFTY_TWO_WEEK_LOW to "52w low",
                AlertType.MA200_CROSS_UP to "MA200 up"
            ),
            selected = type,
            onSelect = onTypeChange
        )
        TriggerRow(
            items = listOf(
                AlertType.MA200_CROSS_DOWN to "MA200 down",
                AlertType.VOLUME_SPIKE to "Volume",
                AlertType.ANALYST_TARGET_REACH to "Target"
            ),
            selected = type,
            onSelect = onTypeChange
        )
        if (!hasEntryPrice) {
            Text(
                "Entry alerts appear after you add a position.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TriggerRow(
    items: List<Pair<AlertType, String>>,
    selected: AlertType,
    onSelect: (AlertType) -> Unit
) {
    if (items.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (alertType, label) ->
            TriggerChip(
                label = label,
                selected = selected == alertType,
                onClick = { onSelect(alertType) }
            )
        }
    }
}

@Composable
private fun TriggerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(9.dp)
            )
            .border(
                2.dp,
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun TriggerValueCard(
    type: AlertType,
    threshold: String,
    onThresholdChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel("Trigger value")
        FilledInput(
            value = threshold,
            onValueChange = onThresholdChange,
            placeholder = thresholdLabel(type),
            keyboardType = KeyboardType.Decimal
        )
        thresholdHint(type)?.let {
            Text(
                it,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AutomaticTriggerCard(type: AlertType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionLabel("Automatic trigger")
        Text(
            automaticText(type),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AlertMeaningCard(
    symbol: String,
    type: AlertType,
    threshold: String,
    marketHoursOnly: Boolean?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(meaningColor(type), CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Plain meaning",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                alertMeaning(symbol, type, threshold, marketHoursOnly),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TimingCard(
    autoDisable: Boolean,
    marketHoursOnly: Boolean?,
    onAutoDisableChange: (Boolean) -> Unit,
    onMarketHoursOnlyChange: (Boolean?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionLabel("Notification timing")
        ToggleLine(
            title = "Auto-disable",
            subtitle = "Best for one-shot targets.",
            checked = autoDisable,
            onCheckedChange = onAutoDisableChange
        )
        SectionLabel("Market hours")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimingChip(
                label = "Global",
                selected = marketHoursOnly == null,
                onClick = { onMarketHoursOnlyChange(null) }
            )
            TimingChip(
                label = "Market only",
                selected = marketHoursOnly == true,
                onClick = { onMarketHoursOnlyChange(true) }
            )
            TimingChip(
                label = "Any time",
                selected = marketHoursOnly == false,
                onClick = { onMarketHoursOnlyChange(false) }
            )
        }
    }
}

@Composable
private fun ToggleLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        label,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(9.dp)
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun NotesCard(
    notes: String,
    onNotesChange: (String) -> Unit
) {
    val noteBorderColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Notification note")
            Spacer(Modifier.width(8.dp))
            OptionalPill()
        }
        BasicTextField(
            value = notes,
            onValueChange = onNotesChange,
            minLines = 2,
            maxLines = 4,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .drawBehind {
                    drawRoundRect(
                        color = noteBorderColor,
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))
                        )
                    )
                }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (notes.isBlank()) {
                    Text(
                        "Optional reason for this alert",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                inner()
            }
        )
    }
}

@Composable
private fun FilledInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.4.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun OptionalPill() {
    Text(
        "OPTIONAL",
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

@Composable
private fun FormActions(
    primaryText: String,
    enabled: Boolean,
    onPrimary: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.heightIn(min = 52.dp)
        ) {
            Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp)
                )
                .clickable(enabled = enabled, onClick = onPrimary)
                .padding(horizontal = 14.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                primaryText,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun AlertType.needsThreshold(): Boolean = when (this) {
    AlertType.FIFTY_TWO_WEEK_HIGH,
    AlertType.FIFTY_TWO_WEEK_LOW,
    AlertType.MA200_CROSS_UP,
    AlertType.MA200_CROSS_DOWN,
    AlertType.ANALYST_TARGET_REACH -> false
    else -> true
}

private fun thresholdLabel(type: AlertType): String = when (type) {
    AlertType.PRICE_ABOVE -> "Price above"
    AlertType.PRICE_BELOW -> "Price below"
    AlertType.PERCENT_CHANGE_DAY -> "Day move %"
    AlertType.PERCENT_ABOVE_ENTRY -> "Gain %"
    AlertType.PERCENT_BELOW_ENTRY -> "Loss %"
    AlertType.EARNINGS_REMINDER -> "Days before results"
    AlertType.VOLUME_SPIKE -> "Volume multiple"
    else -> "Value"
}

private fun thresholdHint(type: AlertType): String? = when (type) {
    AlertType.PRICE_ABOVE -> "Notify once price rises above this level."
    AlertType.PRICE_BELOW -> "Notify once price falls below this level."
    AlertType.PERCENT_CHANGE_DAY -> "Example: 3.5 means a 3.5% daily move."
    AlertType.PERCENT_ABOVE_ENTRY -> "Use a positive number. 10 means +10% gain."
    AlertType.PERCENT_BELOW_ENTRY -> "Use a positive number. 5 means -5% loss."
    AlertType.EARNINGS_REMINDER -> "Example: 1 means one day before results."
    AlertType.VOLUME_SPIKE -> "Example: 2.0 means about 2x normal volume."
    else -> null
}

private fun automaticText(type: AlertType): String = when (type) {
    AlertType.FIFTY_TWO_WEEK_HIGH -> "No number needed. The app watches for a fresh 52-week high."
    AlertType.FIFTY_TWO_WEEK_LOW -> "No number needed. The app watches for a fresh 52-week low."
    AlertType.MA200_CROSS_UP -> "No number needed. The app watches for a cross above the 200-day average."
    AlertType.MA200_CROSS_DOWN -> "No number needed. The app watches for a cross below the 200-day average."
    AlertType.ANALYST_TARGET_REACH -> "No number needed. The app watches the analyst target."
    else -> "No number needed."
}

private fun meaningColor(type: AlertType): Color = when (type) {
    AlertType.PRICE_ABOVE,
    AlertType.PERCENT_ABOVE_ENTRY,
    AlertType.FIFTY_TWO_WEEK_HIGH,
    AlertType.MA200_CROSS_UP,
    AlertType.ANALYST_TARGET_REACH -> Success
    AlertType.EARNINGS_REMINDER,
    AlertType.VOLUME_SPIKE,
    AlertType.PERCENT_CHANGE_DAY -> Warning
    AlertType.PRICE_BELOW,
    AlertType.PERCENT_BELOW_ENTRY,
    AlertType.FIFTY_TWO_WEEK_LOW,
    AlertType.MA200_CROSS_DOWN -> Color(0xFF8A1C1C)
}

@Suppress("UNUSED_VARIABLE")
private fun toneChipColors(type: AlertType): Pair<Color, Color> = when (type) {
    AlertType.PRICE_ABOVE,
    AlertType.PERCENT_ABOVE_ENTRY,
    AlertType.FIFTY_TWO_WEEK_HIGH,
    AlertType.MA200_CROSS_UP,
    AlertType.ANALYST_TARGET_REACH -> SuccessBg to Success
    AlertType.PRICE_BELOW,
    AlertType.PERCENT_BELOW_ENTRY,
    AlertType.FIFTY_TWO_WEEK_LOW,
    AlertType.MA200_CROSS_DOWN -> Color(0xFFFCE4E4) to Color(0xFF8A1C1C)
    else -> WarningBg to Warning
}

private fun alertMeaning(
    symbol: String,
    type: AlertType,
    threshold: String,
    marketHoursOnly: Boolean?
): String {
    val ticker = symbol.ifBlank { "This stock" }.uppercase()
    val value = threshold.ifBlank { "your value" }
    val base = when (type) {
        AlertType.PRICE_ABOVE -> "$ticker notifies above $value."
        AlertType.PRICE_BELOW -> "$ticker notifies below $value."
        AlertType.PERCENT_CHANGE_DAY -> "$ticker notifies when today's move reaches $value%."
        AlertType.PERCENT_ABOVE_ENTRY -> "$ticker notifies when your gain reaches +$value%."
        AlertType.PERCENT_BELOW_ENTRY -> "$ticker notifies when your loss reaches -$value%."
        AlertType.EARNINGS_REMINDER -> "$ticker reminds you $value day(s) before results."
        AlertType.FIFTY_TWO_WEEK_HIGH -> "$ticker notifies at a fresh 52-week high."
        AlertType.FIFTY_TWO_WEEK_LOW -> "$ticker notifies at a fresh 52-week low."
        AlertType.MA200_CROSS_UP -> "$ticker notifies above the 200-day average."
        AlertType.MA200_CROSS_DOWN -> "$ticker notifies below the 200-day average."
        AlertType.VOLUME_SPIKE -> "$ticker notifies when volume is about ${value}x normal."
        AlertType.ANALYST_TARGET_REACH -> "$ticker notifies when price reaches the analyst target."
    }
    val timing = when (marketHoursOnly) {
        true -> " Market-hours only."
        false -> " Any time."
        null -> " Uses global timing."
    }
    return base + timing
}
