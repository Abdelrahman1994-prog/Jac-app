package com.jacdrive.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jacdrive.ui.theme.Readout

/** A time-stamped value for rolling charts. */
data class TimedValue(val tMs: Long, val value: Double)

/**
 * Rolling single-series line chart (2 dp line, soft area) over the last [windowMs]. One per measure
 * — measures with different units are shown as small multiples, never on a second axis.
 * Time always runs left→right, also in RTL, like the instrument cluster.
 */
@Composable
fun RollingLineChart(
    title: String,
    points: List<TimedValue>,
    min: Double,
    max: Double,
    nowMs: Long,
    color: Color,
    current: String,
    modifier: Modifier = Modifier,
    windowMs: Long = 60_000,
    height: Dp = 90.dp,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(title.uppercase(), style = Readout.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(current, style = Readout.Small, color = MaterialTheme.colorScheme.onSurface)
        }
        Box {
            Canvas(Modifier.fillMaxWidth().height(height)) {
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
                val visible = points.filter { nowMs - it.tMs <= windowMs }
                if (visible.size < 2) return@Canvas
                fun x(t: Long) = (1f - (nowMs - t).toFloat() / windowMs) * size.width
                fun y(v: Double) = (size.height - ((v - min) / (max - min)).coerceIn(0.0, 1.0) * size.height).toFloat()
                val line = Path()
                visible.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.tMs), y(p.value)) else line.lineTo(x(p.tMs), y(p.value)) }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(visible.last().tMs), size.height)
                    lineTo(x(visible.first().tMs), size.height)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
                drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = visible.last()
                drawCircle(color, 4.dp.toPx(), Offset(x(last.tMs), y(last.value)))
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
