package com.stockwatchdog.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val NairobiZone: ZoneId = ZoneId.of("Africa/Nairobi")
private val ShortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

enum class FinancialResultTone {
    Upcoming,
    Soon,
    Today,
    Released,
    Beat,
    Miss
}

data class FinancialResultSummary(
    val text: String,
    val tone: FinancialResultTone
)

fun financialResultSummary(
    details: StockDetails?,
    nowMs: Long = System.currentTimeMillis()
): FinancialResultSummary? {
    details ?: return null
    val eventEpoch = details.nextEarningsEpochSeconds ?: return null
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(NairobiZone).toLocalDate()
    val eventDate = Instant.ofEpochSecond(eventEpoch).atZone(NairobiZone).toLocalDate()
    val days = ChronoUnit.DAYS.between(nowDate, eventDate)
    val base = resultBase(details)

    return when {
        days > 10 -> FinancialResultSummary(
            "$base (${eventDateText(details, eventDate)}, ${days}d)",
            FinancialResultTone.Upcoming
        )
        days > 1 -> FinancialResultSummary(
            "$base (${eventDateText(details, eventDate)}, ${days}d)",
            FinancialResultTone.Soon
        )
        days == 1L -> FinancialResultSummary(
            "$base (${estimatePrefix(details)}tomorrow)",
            FinancialResultTone.Soon
        )
        days == 0L -> FinancialResultSummary(
            "$base (today)",
            FinancialResultTone.Today
        )
        days in -3L..-1L -> releasedSummary(details, abs(days))
        else -> null
    }
}

@Composable
fun FinancialResultBadge(
    details: StockDetails?,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis()
) {
    val summary = remember(details, nowMs) { financialResultSummary(details, nowMs) } ?: return
    val color = financialResultColor(summary.tone)

    Text(
        summary.text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun financialResultColor(tone: FinancialResultTone): Color = when (tone) {
    FinancialResultTone.Upcoming -> MaterialTheme.colorScheme.onSurfaceVariant
    FinancialResultTone.Soon -> Color(0xFFFF9800)
    FinancialResultTone.Today -> MaterialTheme.colorScheme.error
    FinancialResultTone.Released -> MaterialTheme.colorScheme.tertiary
    FinancialResultTone.Beat -> PositiveGreen
    FinancialResultTone.Miss -> NegativeRed
}

private fun resultBase(details: StockDetails): String =
    details.nextEarningsQuarterLabel
        ?.takeIf { it.isNotBlank() }
        ?.let { "${compactQuarterLabel(it)} RES" }
        ?: "RESULTS"

private fun eventDateText(details: StockDetails, eventDate: java.time.LocalDate): String =
    estimatePrefix(details) + eventDate.format(ShortDateFormatter)

private fun estimatePrefix(details: StockDetails): String =
    if (details.nextEarningsIsEstimate == true) "~" else ""

private fun compactQuarterLabel(label: String): String {
    val match = Regex("""Q([1-4])\s+(\d{4})""").matchEntire(label.trim().uppercase())
    return if (match != null) {
        val q = match.groupValues[1]
        val year = match.groupValues[2].takeLast(2)
        "Q$q'$year"
    } else {
        label
    }
}

private fun releasedSummary(details: StockDetails, daysAgo: Long): FinancialResultSummary {
    val surprise = details.epsSurprisePct()
    if (surprise != null) {
        return when {
            surprise > 0.5 -> FinancialResultSummary(
                "EPS BEAT (${compactSignedPercent(surprise)})",
                FinancialResultTone.Beat
            )
            surprise < -0.5 -> FinancialResultSummary(
                "EPS MISS (${compactSignedPercent(surprise)})",
                FinancialResultTone.Miss
            )
            else -> FinancialResultSummary(
                "EPS INLINE (${compactSignedPercent(surprise)})",
                FinancialResultTone.Released
            )
        }
    }
    return FinancialResultSummary("RESULTS OUT (${daysAgo}d)", FinancialResultTone.Released)
}

private fun compactSignedPercent(value: Double): String =
    String.format(Locale.US, "%+.1f%%", value)
