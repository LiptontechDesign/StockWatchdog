package com.stockwatchdog.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val priceFormat = DecimalFormat("#,##0.00")
private val wholeFormat = DecimalFormat("#,##0")
private val signedPct = DecimalFormat("+#,##0.00;-#,##0.00")

fun formatPrice(value: Double?, currency: String? = null): String {
    if (value == null) return "--"
    val base = priceFormat.format(value)
    return if (!currency.isNullOrBlank() && currency != "USD") "$base $currency" else base
}

fun formatSignedPercent(value: Double?): String =
    if (value == null) "--" else signedPct.format(value) + "%"

fun formatSignedChange(value: Double?): String =
    if (value == null) "--" else signedPct.format(value).trimEnd('%')

fun formatVolume(value: Long?): String {
    if (value == null) return "--"
    val v = value.toDouble()
    return when {
        v >= 1_000_000_000 -> "${"%.2f".format(v / 1_000_000_000)}B"
        v >= 1_000_000 -> "${"%.2f".format(v / 1_000_000)}M"
        v >= 1_000 -> "${"%.1f".format(v / 1_000)}K"
        else -> wholeFormat.format(v)
    }
}

fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

@Composable
fun changeColor(value: Double?): Color = when {
    value == null -> MaterialTheme.colorScheme.onSurface
    value > 0 -> PositiveGreen
    value < 0 -> NegativeRed
    else -> MaterialTheme.colorScheme.onSurface
}
