package com.jacdrive.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jacdrive.ui.theme.Readout

data class BarDatum(val label: String, val value: Double?)

/**
 * Single-series bar chart: thin bars rounded only at the data end, 2 dp gaps, recessive baseline
 * and one dashed reference line at the maximum. Tap a bar to read its exact value. Null values
 * (no data) draw nothing — they are not zero.
 */
@Composable
fun BarChart(
    data: List<BarDatum>,
    color: Color,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    labelEvery: Int = 1,
) {
    var selected by remember(data) { mutableIntStateOf(data.indexOfLast { (it.value ?: 0.0) > 0 }) }
    val max = data.maxOfOrNull { it.value ?: 0.0 }?.takeIf { it > 0 } ?: 1.0
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(modifier) {
        val sel = data.getOrNull(selected)
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                if (sel?.value != null) "${sel.label} · ${formatValue(sel.value)}" else " ",
                style = Readout.Small, color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(
                Modifier.fillMaxWidth().height(height).pointerInput(data) {
                    detectTapGestures { p ->
                        val n = data.size.coerceAtLeast(1)
                        var i = (p.x / (size.width / n)).toInt().coerceIn(0, n - 1)
                        if (rtl) i = n - 1 - i
                        selected = i
                    }
                },
            ) {
                val n = data.size.coerceAtLeast(1)
                val slot = size.width / n
                val gap = 2.dp.toPx()
                val barW = (slot - gap).coerceAtMost(28.dp.toPx())
                val radius = minOf(4.dp.toPx(), barW / 2)
                // Reference line at the maximum (recessive, dashed).
                drawLine(grid, Offset(0f, 0f), Offset(size.width, 0f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                data.forEachIndexed { idx, d ->
                    val v = d.value ?: return@forEachIndexed
                    if (v <= 0) return@forEachIndexed
                    val h = (v / max * size.height).toFloat().coerceAtLeast(2.dp.toPx())
                    val slotIndex = if (rtl) n - 1 - idx else idx
                    val left = slotIndex * slot + (slot - barW) / 2
                    val top = size.height - h
                    val c = if (idx == selected) color else color.copy(alpha = 0.55f)
                    // Rounded data end, square at the baseline.
                    val path = Path().apply {
                        moveTo(left, size.height)
                        lineTo(left, top + radius)
                        quadraticTo(left, top, left + radius, top)
                        lineTo(left + barW - radius, top)
                        quadraticTo(left + barW, top, left + barW, top + radius)
                        lineTo(left + barW, size.height)
                        close()
                    }
                    drawPath(path, c)
                }
            }
            Text(formatValue(max), style = Readout.Caption, color = ink, modifier = Modifier.padding(top = 2.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            data.forEachIndexed { i, d ->
                Text(
                    if (i % labelEvery == 0) d.label else "",
                    style = Readout.Caption, color = ink, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

