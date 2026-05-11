package com.stockwatchdog.app.ui.dip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockwatchdog.app.data.api.StockDetails
import com.stockwatchdog.app.ui.components.formatPrice
import com.stockwatchdog.app.ui.diptracker.DipRow
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen
import com.stockwatchdog.app.util.MarketClock

private val InBuyZone = PositiveGreen
private val StrongBuy = Color(0xFF2E7D32)
private val Earnings = Color(0xFF6C5CE7)
private val Target = Color(0xFF009688)
private val MA200 = Color(0xFFFF8A65)
private val VolumeSpike = Color(0xFFFFC107)

// ════════════════════════════════════════════════════════════════════════
// 52-week range bar with current + analyst-target markers
// ════════════════════════════════════════════════════════════════════════

@Composable
internal fun FiftyTwoWeekRangeBar(
    details: StockDetails,
    currentPrice: Double?,
    statusColor: Color
) {
    val low = details.fiftyTwoWeekLow ?: return
    val high = details.fiftyTwoWeekHigh ?: return
    if (high <= low) return

    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val ringColor = MaterialTheme.colorScheme.surface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    fun fraction(value: Double): Float =
        ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "52-week range",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            currentPrice?.let { price ->
                val pos = ((price - low) / (high - low) * 100).toInt().coerceIn(0, 100)
                Text(
                    "$pos% of range",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            val cy = size.height / 2f
            val barH = 5.dp.toPx()
            val r = barH / 2f
            val top = cy - barH / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, barH),
                cornerRadius = CornerRadius(r, r)
            )

            // Coloured fill from low to current price.
            currentPrice?.let { price ->
                val frac = fraction(price)
                drawRoundRect(
                    color = statusColor.copy(alpha = 0.55f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width * frac, barH),
                    cornerRadius = CornerRadius(r, r)
                )
            }

            // Analyst target marker (vertical tick) if present.
            details.analystTargetMean?.let { tgt ->
                if (tgt in low..high) {
                    val x = fraction(tgt) * size.width
                    drawRoundRect(
                        color = Target,
                        topLeft = Offset(x - 1.dp.toPx(), top - 4.dp.toPx()),
                        size = Size(2.dp.toPx(), barH + 8.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }

            // 200-day MA marker (vertical tick) if present.
            details.twoHundredDayAverage?.let { ma ->
                if (ma in low..high) {
                    val x = fraction(ma) * size.width
                    drawRoundRect(
                        color = MA200,
                        topLeft = Offset(x - 1.dp.toPx(), top - 2.dp.toPx()),
                        size = Size(2.dp.toPx(), barH + 4.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }

            // Current price marker dot.
            currentPrice?.let { price ->
                val x = fraction(price) * size.width
                drawCircle(color = ringColor, radius = 7.dp.toPx(), center = Offset(x, cy))
                drawCircle(color = statusColor, radius = 5.dp.toPx(), center = Offset(x, cy))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Low ${formatPrice(low)}",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
            Text(
                "High ${formatPrice(high)}",
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Buy zone bar — focused on the user-defined zones
// ════════════════════════════════════════════════════════════════════════

@Composable
internal fun BuyZoneBar(row: DipRow, statusColor: Color) {
    val low = row.entity.buyZoneLow
    val high = row.entity.buyZoneHigh
    val current = row.currentPrice
    val strongBuy = row.entity.strongBuyBelow

    val rawMin = minOf(low, strongBuy ?: low, current ?: low)
    val rawMax = maxOf(high, current ?: high)
    val span = (rawMax - rawMin).coerceAtLeast((high - low).coerceAtLeast(high * 0.04))
    val minValue = (rawMin - span * 0.08).coerceAtLeast(0.0)
    val maxValue = rawMax + span * 0.08

    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val ringColor = MaterialTheme.colorScheme.surface
    val zoneColor = InBuyZone.copy(alpha = 0.72f)
    val strongColor = StrongBuy.copy(alpha = 0.78f)

    fun fraction(v: Double): Float =
        ((v - minValue) / (maxValue - minValue)).toFloat().coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Buy zone ${formatPrice(low)} – ${formatPrice(high)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            strongBuy?.let {
                Text(
                    "Strong ${formatPrice(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StrongBuy,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val cy = size.height / 2f
            val barH = 5.dp.toPx()
            val r = barH / 2f
            val top = cy - barH / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, barH),
                cornerRadius = CornerRadius(r, r)
            )
            strongBuy?.let {
                val end = fraction(it) * size.width
                drawRoundRect(
                    color = strongColor,
                    topLeft = Offset(0f, top),
                    size = Size(end.coerceAtLeast(3.dp.toPx()), barH),
                    cornerRadius = CornerRadius(r, r)
                )
            }
            val zStart = fraction(low) * size.width
            val zEnd = fraction(high) * size.width
            drawRoundRect(
                color = zoneColor,
                topLeft = Offset(zStart, top),
                size = Size((zEnd - zStart).coerceAtLeast(4.dp.toPx()), barH),
                cornerRadius = CornerRadius(r, r)
            )
            current?.let {
                val x = fraction(it) * size.width
                drawCircle(color = ringColor, radius = 6.dp.toPx(), center = Offset(x, cy))
                drawCircle(color = statusColor, radius = 4.dp.toPx(), center = Offset(x, cy))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Metric grid (earnings countdown, last EPS, target, MA200, volume)
// ════════════════════════════════════════════════════════════════════════

@Composable
internal fun MetricsGrid(row: DipRow, nowMs: Long) {
    val d = row.details

    // Build metric tiles only for data we actually have.
    val tiles = buildList {
        // Earnings countdown
        d?.nextEarningsEpochSeconds?.let { eps ->
            val days = MarketClock.daysUntil(eps, nowMs)
            val sub = if (days >= 0) MarketClock.formatKenyaLong(eps) else "Reported recently"
            val title = if (days < 0) "Earnings: just past"
            else if (days == 0L) "Earnings TODAY"
            else "Earnings in $days day${if (days == 1L) "" else "s"}"
            val estimate = d.nextEarningsIsEstimate == true
            add(
                MetricTile(
                    icon = Icons.Default.CalendarMonth,
                    color = Earnings,
                    title = title + if (estimate) " (est.)" else "",
                    sub = sub
                )
            )
        }

        // Last quarter EPS surprise
        val surprise = d?.epsSurprisePct()
        if (surprise != null) {
            val beat = surprise >= 0
            val label = d?.lastEpsQuarterLabel?.let { "Last Q ($it)" } ?: "Last quarter"
            add(
                MetricTile(
                    icon = if (beat) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    color = if (beat) PositiveGreen else NegativeRed,
                    title = (if (beat) "Beat by " else "Missed by ") +
                        "%.1f%%".format(kotlin.math.abs(surprise)),
                    sub = label
                )
            )
        }

        // Analyst target
        val upside = d?.upsideToTargetPct(row.currentPrice)
        if (upside != null && d.analystTargetMean != null) {
            val positive = upside >= 0
            add(
                MetricTile(
                    icon = if (positive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    color = Target,
                    title = "Target ${formatPrice(d.analystTargetMean)}",
                    sub = (if (positive) "+%.1f%% upside" else "%.1f%% downside").format(
                        if (positive) upside else kotlin.math.abs(upside)
                    ) + (d.analystOpinionsCount?.let { " · $it analysts" } ?: "")
                )
            )
        }

        // MA200 distance
        val maPct = d?.pctVs200dMa(row.currentPrice)
        if (maPct != null && d?.twoHundredDayAverage != null) {
            val above = maPct >= 0
            add(
                MetricTile(
                    icon = Icons.Default.ShowChart,
                    color = MA200,
                    title = (if (above) "+%.1f%% vs MA200" else "%.1f%% vs MA200").format(maPct),
                    sub = "MA200 ${formatPrice(d.twoHundredDayAverage)}"
                )
            )
        }

        // Volume spike (only if meaningful)
        val ratio = d?.volumeSpikeRatio()
        if (ratio != null && ratio >= 1.5) {
            add(
                MetricTile(
                    icon = Icons.Default.Bolt,
                    color = VolumeSpike,
                    title = "Volume %.1f×".format(ratio),
                    sub = "Unusual activity"
                )
            )
        }
    }

    if (tiles.isEmpty()) return

    // Render in 2-column grid using rows.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { t -> MetricTileView(t, modifier = Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

internal data class MetricTile(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val title: String,
    val sub: String
)

@Composable
internal fun MetricTileView(tile: MetricTile, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(tile.color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            tile.icon,
            contentDescription = null,
            tint = tile.color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tile.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                tile.sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// Empty state
// ════════════════════════════════════════════════════════════════════════

@Composable
internal fun EmptyDipState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.TrendingDown,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )
        Text(
            "No stocks yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Add stocks you want to buy on a dip. You'll see live status, " +
                "next earnings date, analyst targets and 52-week range — all in Kenya time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add your first stock")
        }
    }
}
