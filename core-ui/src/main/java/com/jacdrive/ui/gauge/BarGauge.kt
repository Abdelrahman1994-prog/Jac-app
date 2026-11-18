package com.jacdrive.ui.gauge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Segmented horizontal bar. Fills from the layout start edge, so it reads right-to-left in Arabic.
 *
 * @param fraction 0..1, or null when there is no data.
 * @param colorFor colour of a lit segment given its position 0..1.
 */
@Composable
fun BarGauge(
    fraction: Float?,
    modifier: Modifier = Modifier,
    segments: Int = 24,
    height: Dp = 8.dp,
    trackColor: Color,
    animate: Boolean = true,
    colorFor: (Float) -> Color,
) {
    val target = fraction?.coerceIn(0f, 1f) ?: 0f
    val f by animateFloatAsState(target, if (animate) tween(220) else snap(), label = "bar")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = size.height * 0.35f
        val segW = (size.width - gap * (segments - 1)) / segments
        val radius = CornerRadius(size.height * 0.25f)
        for (i in 0 until segments) {
            val t = (i + 0.5f) / segments
            val lit = fraction != null && t <= f
            val logicalX = i * (segW + gap)
            val x = if (rtl) size.width - logicalX - segW else logicalX
            drawRoundRect(
                color = if (lit) colorFor(i / (segments - 1f).coerceAtLeast(1f)) else trackColor,
                topLeft = Offset(x, 0f),
                size = Size(segW, size.height),
                cornerRadius = radius,
            )
        }
    }
}
