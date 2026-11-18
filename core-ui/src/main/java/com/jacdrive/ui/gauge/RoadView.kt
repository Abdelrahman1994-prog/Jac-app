package com.jacdrive.ui.gauge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.jacdrive.ui.cockpit.CockpitStyle
import kotlin.math.pow

/**
 * Decorative perspective road with the vehicle seen from behind. Lane marks flow with vehicle
 * speed when animations are on. Purely illustrative: it does not show detected objects or lanes.
 */
@Composable
fun RoadView(speedKmh: Float, style: CockpitStyle, modifier: Modifier = Modifier, animate: Boolean = true) {
    val phase = if (animate && speedKmh > 1f) {
        val periodMs = (900_000f / speedKmh.coerceIn(5f, 200f)).toInt().coerceIn(300, 12_000)
        rememberInfiniteTransition(label = "road").animateFloat(
            0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "roadPhase",
        ).value
    } else 0f

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val vanish = Offset(w / 2, h * 0.06f)
        val bottomHalf = w * 0.46f

        // Lane edges: bright near the car, fading into the distance.
        listOf(-1f, 1f).forEach { side ->
            drawLine(
                Brush.verticalGradient(listOf(Color.Transparent, style.tick.copy(alpha = 0.85f)), startY = vanish.y, endY = h),
                start = Offset(vanish.x + side * w * 0.02f, vanish.y),
                end = Offset(w / 2 + side * bottomHalf, h),
                strokeWidth = w * 0.012f,
                cap = StrokeCap.Round,
            )
        }

        // Flowing centre dashes (perspective-spaced).
        val dashes = 7
        for (i in 0 until dashes) {
            val t = ((i + phase) / dashes)
            val y0 = vanish.y + (h - vanish.y) * t.pow(2.2f)
            val y1 = vanish.y + (h - vanish.y) * (t + 0.05f).coerceAtMost(1f).pow(2.2f)
            drawLine(
                style.tick.copy(alpha = 0.15f + 0.35f * t),
                Offset(w / 2, y0), Offset(w / 2, y1),
                strokeWidth = w * (0.004f + 0.01f * t),
            )
        }

        drawCar(Offset(w / 2, h * 0.80f), w * 0.30f, style)
    }
}

/** Rear view of a compact SUV, centred on [center] with total width [cw]. */
private fun DrawScope.drawCar(center: Offset, cw: Float, style: CockpitStyle) {
    val ch = cw * 0.78f
    val left = center.x - cw / 2
    val top = center.y - ch / 2

    // Ground shadow and accent glow.
    drawOval(Brush.radialGradient(listOf(style.accent.copy(alpha = 0.25f), Color.Transparent), center = Offset(center.x, top + ch), radius = cw * 0.7f),
        topLeft = Offset(left - cw * 0.2f, top + ch * 0.82f), size = Size(cw * 1.4f, ch * 0.4f))
    drawOval(Color.Black.copy(alpha = 0.55f), topLeft = Offset(left - cw * 0.04f, top + ch * 0.88f), size = Size(cw * 1.08f, ch * 0.2f))

    // Tyres.
    val tyre = Color(0xFF0B0E12)
    drawRoundRect(tyre, Offset(left + cw * 0.04f, top + ch * 0.78f), Size(cw * 0.18f, ch * 0.22f), CornerRadius(cw * 0.03f))
    drawRoundRect(tyre, Offset(left + cw * 0.78f, top + ch * 0.78f), Size(cw * 0.18f, ch * 0.22f), CornerRadius(cw * 0.03f))

    val bodyTop = Color(0xFF7B8CA3)
    val bodyBottom = Color(0xFF3C4858)
    // Lower body.
    val body = Path().apply {
        moveTo(left + cw * 0.04f, top + ch * 0.42f)
        lineTo(left + cw * 0.96f, top + ch * 0.42f)
        quadraticTo(left + cw, top + ch * 0.46f, left + cw, top + ch * 0.56f)
        lineTo(left + cw * 0.98f, top + ch * 0.84f)
        quadraticTo(left + cw * 0.97f, top + ch * 0.9f, left + cw * 0.9f, top + ch * 0.9f)
        lineTo(left + cw * 0.1f, top + ch * 0.9f)
        quadraticTo(left + cw * 0.03f, top + ch * 0.9f, left + cw * 0.02f, top + ch * 0.84f)
        lineTo(left, top + ch * 0.56f)
        quadraticTo(left, top + ch * 0.46f, left + cw * 0.04f, top + ch * 0.42f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(bodyTop, bodyBottom), startY = top + ch * 0.42f, endY = top + ch * 0.9f))

    // Cabin / rear window.
    val cabin = Path().apply {
        moveTo(left + cw * 0.14f, top + ch * 0.44f)
        lineTo(left + cw * 0.24f, top + ch * 0.08f)
        quadraticTo(left + cw * 0.26f, top + ch * 0.02f, left + cw * 0.32f, top + ch * 0.02f)
        lineTo(left + cw * 0.68f, top + ch * 0.02f)
        quadraticTo(left + cw * 0.74f, top + ch * 0.02f, left + cw * 0.76f, top + ch * 0.08f)
        lineTo(left + cw * 0.86f, top + ch * 0.44f)
        close()
    }
    drawPath(cabin, Brush.verticalGradient(listOf(Color(0xFF8C9DB3), bodyTop), startY = top, endY = top + ch * 0.44f))
    val glass = Path().apply {
        moveTo(left + cw * 0.22f, top + ch * 0.36f)
        lineTo(left + cw * 0.29f, top + ch * 0.10f)
        lineTo(left + cw * 0.71f, top + ch * 0.10f)
        lineTo(left + cw * 0.78f, top + ch * 0.36f)
        close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF1C2734), Color(0xFF2E3D50)), startY = top + ch * 0.1f, endY = top + ch * 0.36f))

    // Full-width light bar and tail lamps.
    val lamp = style.critical
    drawRoundRect(lamp.copy(alpha = 0.25f), Offset(left + cw * 0.02f, top + ch * 0.46f), Size(cw * 0.96f, ch * 0.1f), CornerRadius(ch * 0.05f))
    drawRoundRect(lamp, Offset(left + cw * 0.05f, top + ch * 0.49f), Size(cw * 0.2f, ch * 0.045f), CornerRadius(ch * 0.02f))
    drawRoundRect(lamp, Offset(left + cw * 0.75f, top + ch * 0.49f), Size(cw * 0.2f, ch * 0.045f), CornerRadius(ch * 0.02f))
    drawLine(lamp.copy(alpha = 0.8f), Offset(left + cw * 0.25f, top + ch * 0.51f), Offset(left + cw * 0.75f, top + ch * 0.51f), strokeWidth = ch * 0.012f)

    // Plate recess.
    drawRoundRect(Color(0xFF283240), Offset(center.x - cw * 0.14f, top + ch * 0.62f), Size(cw * 0.28f, ch * 0.1f), CornerRadius(ch * 0.02f))
}
