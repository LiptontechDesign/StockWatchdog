package com.stockwatchdog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stockwatchdog.app.domain.PricePoint
import com.stockwatchdog.app.ui.theme.NegativeRed
import com.stockwatchdog.app.ui.theme.PositiveGreen

/**
 * Lightweight line chart implemented directly on Compose Canvas.
 *
 * Design goals (per app requirements):
 *  - No heavy chart dependency.
 *  - Fast and smooth for 30-100 points which is what the app renders.
 *  - Clear and readable, subtle gridlines only.
 */
@Composable
fun PriceLineChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    isPositive: Boolean? = null
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No chart data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val min = points.minOf { it.close }
    val max = points.maxOf { it.close }
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    val lineColor = when {
        isPositive == true -> PositiveGreen
        isPositive == false -> NegativeRed
        points.last().close >= points.first().close -> PositiveGreen
        else -> NegativeRed
    }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paddingV = h * 0.08f
        val usableH = h - paddingV * 2
        val stepX = if (points.size == 1) 0f else w / (points.size - 1).toFloat()

        // Light horizontal gridlines (4 divisions)
        val gridStroke = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f))
        for (i in 1..3) {
            val y = paddingV + usableH * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = gridStroke.pathEffect
            )
        }

        val path = Path()
        val fill = Path()
        points.forEachIndexed { i, pt ->
            val x = i * stepX
            val norm = ((pt.close - min) / range).toFloat()
            val y = paddingV + usableH * (1f - norm)
            if (i == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo((points.size - 1) * stepX, h)
        fill.close()

        drawPath(
            path = fill,
            color = lineColor.copy(alpha = 0.12f)
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
    }
}

private fun Color.copyAlpha(a: Float): Color = copy(alpha = a)
