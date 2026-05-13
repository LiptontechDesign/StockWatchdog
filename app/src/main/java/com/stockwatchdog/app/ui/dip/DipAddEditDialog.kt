package com.stockwatchdog.app.ui.dip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import com.stockwatchdog.app.domain.Quote
import com.stockwatchdog.app.domain.SymbolMatch
import com.stockwatchdog.app.ui.components.SearchResultRow
import com.stockwatchdog.app.ui.components.formatPrice
import java.util.Locale
import kotlin.math.abs

private val SheetBg = Color(0xFFF0EFF7)
private val ScreenDark = Color(0xFF1A1830)
private val TextDark = Color(0xFF0D0B1E)
private val Navy = Color(0xFF1E1852)
private val NavyMid = Color(0xFF3D3894)
private val BorderLavender = Color(0xFFB0AEC8)
private val CardBorder = Color(0xFFC8C5E8)
private val PriceFill = Color(0xFFEDEAFB)
private val BarBg = Color(0xFFD8D5F0)
private val SubText = Color(0xFF2D2860)
private val Warning = Color(0xFFC47A00)

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
    val low = parsePrice(buyZoneLow)
    val high = parsePrice(buyZoneHigh)
    val strong = parsePrice(strongBuy)
    val selectedTicker = selectedSymbol?.symbol ?: symbol.takeIf { it.isNotBlank() }
    val zoneError = low != null && high != null && low > high
    val strongError = strong != null && low != null && strong > low
    val canSave = selectedTicker != null && low != null && high != null &&
        low > 0 && high > 0 && !zoneError && !strongError

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
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    if (isEditing) "Edit dip setup" else "Add dip setup",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(18.dp))

                if (selectedTicker == null && !isEditing) {
                    SymbolSearchBlock(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectSymbol = onSelectSymbol,
                        onUseDirect = { onSymbolChange(searchQuery.trim().uppercase(Locale.US)) }
                    )
                } else {
                    StockChip(
                        symbol = selectedTicker ?: symbol,
                        name = selectedSymbol?.name ?: selectedQuote?.name,
                        quote = selectedQuote,
                        canClear = !isEditing,
                        onClear = onClearSymbol
                    )
                    SectionLabel("Quick presets - dip %")
                    PresetRow(
                        currentPrice = selectedQuote?.price,
                        selectedHigh = high,
                        onApplyPresetBuyZone = onApplyPresetBuyZone
                    )
                    ZoneCard(
                        low = buyZoneLow,
                        high = buyZoneHigh,
                        lowValue = low,
                        highValue = high,
                        currentPrice = selectedQuote?.price,
                        zoneError = zoneError,
                        onLowChange = onBuyZoneLowChange,
                        onHighChange = onBuyZoneHighChange
                    )
                    StrongBuyCard(
                        value = strongBuy,
                        valueNumber = strong,
                        low = low,
                        currentPrice = selectedQuote?.price,
                        strongError = strongError,
                        onValueChange = onStrongBuyChange,
                        onApplyPresetStrongBuy = onApplyPresetStrongBuy
                    )
                    ActionRow(
                        canSave = canSave,
                        isEditing = isEditing,
                        onDismiss = onDismiss,
                        onSave = onSave
                    )
                }
            }
        }
    }
}

@Composable
private fun SymbolSearchBlock(
    searchQuery: String,
    searchResults: List<SymbolMatch>,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectSymbol: (SymbolMatch) -> Unit,
    onUseDirect: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Stock")
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search ticker or company") },
            placeholder = { Text("AAPL, Apple, SPY") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            singleLine = true
        )
        when {
            isSearching -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Searching...", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            searchResults.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                ) {
                    items(searchResults, key = { it.symbol + (it.exchange ?: "") }) { match ->
                        SearchResultRow(
                            symbol = match.symbol,
                            subtitle = listOfNotNull(match.name, match.exchange, match.type)
                                .joinToString(" | "),
                            onClick = { onSelectSymbol(match) }
                        )
                    }
                }
            }
            searchQuery.isNotBlank() -> {
                TextButton(onClick = onUseDirect, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Use ${searchQuery.trim().uppercase(Locale.US)} directly")
                }
            }
        }
    }
}

@Composable
private fun StockChip(
    symbol: String,
    name: String?,
    quote: Quote?,
    canClear: Boolean,
    onClear: () -> Unit
) {
    val chipContainer = MaterialTheme.colorScheme.primaryContainer
    val onChip = MaterialTheme.colorScheme.onPrimaryContainer
    val mutedChip = onChip.copy(alpha = 0.72f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(chipContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                symbol,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = onChip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                name ?: "",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = mutedChip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (quote != null) {
            Column(
                modifier = Modifier
                    .background(onChip.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    formatPrice(quote.price, quote.currency),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = onChip
                )
                Text(
                    "Last price",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = mutedChip
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        if (canClear) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .padding(7.dp)
                    .background(onChip.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove $symbol", tint = onChip)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(Locale.US),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.4.sp
    )
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun PresetRow(
    currentPrice: Double?,
    selectedHigh: Double?,
    onApplyPresetBuyZone: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf(5.0, 10.0, 15.0, 20.0).forEach { percent ->
            val selected = currentPrice != null &&
                selectedHigh.isAbout(currentPrice * (1 - percent / 100))
            FlatChoiceButton(
                text = "-${percent.toInt()}%",
                selected = selected,
                modifier = Modifier.weight(1f),
                onClick = { onApplyPresetBuyZone(percent) }
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ZoneCard(
    low: String,
    high: String,
    lowValue: Double?,
    highValue: Double?,
    currentPrice: Double?,
    zoneError: Boolean,
    onLowChange: (String) -> Unit,
    onHighChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ZonePriceField(
                label = "Zone low",
                value = low,
                onValueChange = onLowChange,
                isError = zoneError,
                modifier = Modifier.weight(1f)
            )
            Text(
                "->",
                modifier = Modifier.padding(bottom = 20.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            ZonePriceField(
                label = "Zone high",
                value = high,
                onValueChange = onHighChange,
                isError = zoneError,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))
        DipZoneBar(active = lowValue != null && highValue != null && !zoneError)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                priceDistance(lowValue, currentPrice) ?: "",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                priceDistance(highValue, currentPrice) ?: "",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (zoneError) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Zone high must be greater than zone low.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ZonePriceField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            label.uppercase(Locale.US),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
                .border(
                    2.dp,
                    if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(9.dp)
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = exactInputStyle(),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.isBlank()) {
                        Text(
                            "Tap price",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\$", style = exactInputStyle())
                            innerTextField()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DipZoneBar(active: Boolean) {
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val knobFill = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
    ) {
        val y = size.height / 2
        drawRoundRect(
            color = inactive,
            topLeft = Offset(0f, y - 5.dp.toPx()),
            size = Size(size.width, 10.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
        if (active) {
            val left = size.width * 0.15f
            val right = size.width * 0.85f
            drawLine(
                color = activeColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round
            )
            listOf(left, right).forEach { x ->
                drawCircle(color = knobFill, radius = 9.dp.toPx(), center = Offset(x, y))
                drawCircle(
                    color = activeColor,
                    radius = 9.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun StrongBuyCard(
    value: String,
    valueNumber: Double?,
    low: Double?,
    currentPrice: Double?,
    strongError: Boolean,
    onValueChange: (String) -> Unit,
    onApplyPresetStrongBuy: (Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(15.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", color = Warning, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Strong buy below",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "OPTIONAL",
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                letterSpacing = 0.4.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Set an even lower price - your \"back up the truck\" level.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(10.dp))
        if (low != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(5.0, 10.0, 15.0).forEach { percent ->
                    val selected = valueNumber.isAbout(low * (1 - percent / 100))
                    FlatChoiceButton(
                        text = "-${percent.toInt()}%",
                        selected = selected,
                        small = true,
                        modifier = Modifier.weight(1f),
                        onClick = { onApplyPresetStrongBuy(percent) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        DashedPriceInput(
            value = value,
            isError = strongError,
            supportingText = priceDistance(valueNumber, currentPrice) ?: "Tap a % or type a price",
            onValueChange = onValueChange
        )
        if (strongError) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Strong buy must be at or below zone low.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun DashedPriceInput(
    value: String,
    isError: Boolean,
    supportingText: String,
    onValueChange: (String) -> Unit
) {
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    val inputTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(9.dp))
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    size = size,
                    cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                )
            }
            .padding(horizontal = 11.dp, vertical = 11.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(
            color = inputTextColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (value.isBlank()) {
                    Text(
                        supportingText,
                        color = inputTextColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("\$", color = inputTextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        innerTextField()
                    }
                }
            }
        }
    )
}

@Composable
private fun ActionRow(
    canSave: Boolean,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .background(
                    if (canSave) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .clickable(enabled = canSave, onClick = onSave)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isEditing) "Save dip setup" else "Add to dip tracker",
                color = if (canSave) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun FlatChoiceButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = if (small) 40.dp else 44.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(if (small) 8.dp else 9.dp)
            )
            .border(
                2.dp,
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(if (small) 8.dp else 9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = if (small) 7.dp else 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = if (small) 12.sp else 13.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun exactInputStyle(): TextStyle =
    TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )

private fun parsePrice(value: String): Double? =
    value.replace(",", ".").toDoubleOrNull()

private fun Double?.isAbout(other: Double): Boolean =
    this != null && abs(this - other) < 0.02

private fun priceDistance(value: Double?, currentPrice: Double?): String? {
    if (value == null || currentPrice == null || currentPrice <= 0) return null
    val pct = (value / currentPrice - 1.0) * 100.0
    val sign = if (pct > 0) "+" else ""
    return "$sign${String.format(Locale.US, "%.1f", pct)}% from now"
}
