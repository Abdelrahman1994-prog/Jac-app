package com.jacdrive.dashboard.dash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.DashboardBackground
import com.jacdrive.ui.cockpit.CockpitStyle
import com.jacdrive.ui.cockpit.GaugeStyle
import kotlin.math.pow
import kotlin.random.Random

/** Colours for the dashboard in normal (blue/cyan) and Sport (red/orange) mode. */
data class DashPalette(
    val accent: Color,
    val accent2: Color,
    val glow: Color,
    val speedStyle: CockpitStyle,
    val rpmStyle: CockpitStyle,
) {
    companion object {
        val Cyan = Color(0xFF3FD4FF)
        val Blue = Color(0xFF1E6BFF)
        val Red = Color(0xFFFF3B3B)
        val Orange = Color(0xFFFF7A1A)
        val Amber = Color(0xFFFFB020)
        val Green = Color(0xFF33E28A)
        val Purple = Color(0xFFA35BFF)
        val Teal = Color(0xFF2DE2E6)

        private fun style(accent: Color, secondary: Color, glow: Boolean, width: Float) = CockpitStyle(
            id = CockpitThemeId.JAC_ORIGINAL,
            backgroundTop = Color.Transparent, backgroundBottom = Color.Transparent,
            defaultBackground = DashboardBackground.GRADIENT,
            panel = Color(0x22102A55), panelBorder = accent.copy(alpha = 0.55f),
            accent = accent, accentSecondary = secondary,
            text = Color.White, textDim = Color(0xFFCFE3FF),
            track = Color(0x2A6FA8FF), tick = Color(0xFFE6F1FF),
            ok = Green, warning = Amber, critical = Red,
            gauge = GaugeStyle(sweepDegrees = 260f, arcWidthFraction = width, segmented = false, showNeedle = true, glow = glow, bezel = true),
        )

        val Normal = DashPalette(Cyan, Blue, Cyan, style(Cyan, Blue, true, 0.04f), style(Red, Blue, true, 0.04f))
        val Sport = DashPalette(Orange, Red, Red, style(Orange, Red, true, 0.055f), style(Red, Orange, true, 0.06f))
    }
}

/**
 * Night city skyline, perspective road with light streaks, lane marks that flow with the real
 * vehicle speed, and a stylised SUV front view. Original artwork (not the manufacturer's).
 */
/**
 * Distance in metres travelled at [speedKmh], integrated every display frame while [enabled].
 * Drives the road animation, so lane marks pass at the car's real speed and stop when it stops.
 */
@Composable
fun rememberRoadDistance(speedKmh: Float, enabled: Boolean): State<Float> {
    val travelled = remember { mutableFloatStateOf(0f) }
    val currentSpeed by rememberUpdatedState(speedKmh)
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    val dt = ((t - last) / 1e9f).coerceAtMost(0.1f)
                    travelled.floatValue = (travelled.floatValue + dt * currentSpeed / 3.6f) % 10_000f
                }
                last = t
            }
        }
    }
    return travelled
}

@Composable
fun HeroScene(speedKmh: Float, palette: DashPalette, animate: Boolean, modifier: Modifier = Modifier) {
    // Smooth the 1–10 Hz OBD speed so the road never jumps between samples.
    val speed by animateFloatAsState(speedKmh.coerceAtLeast(0f), tween(600, easing = LinearEasing), label = "heroSpeed")
    val currentSpeed by rememberUpdatedState(speed)
    val travelled by rememberRoadDistance(speed, enabled = animate && speedKmh > 0.5f)
    // Brake lights: on while the real speed is dropping by more than ~1.5 m/s².
    var braking by remember { mutableStateOf(false) }
    var prev by remember { mutableStateOf(speedKmh to System.nanoTime()) }
    LaunchedEffect(speedKmh) {
        val now = System.nanoTime()
        val dt = (now - prev.second) / 1e9f
        if (dt in 0.05f..3f) braking = (prev.first - speedKmh) / 3.6f / dt > 1.5f && speedKmh > 1f
        prev = speedKmh to now
    }
    val windows = remember { Random(7).let { r -> List(260) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val buildings = remember { Random(3).let { r -> List(26) { r.nextFloat() } } }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.52f
        // Sky glow.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF071530), Color(0xFF0B2350), Color(0xFF06101F)), endY = horizon))
        drawRect(Brush.radialGradient(listOf(palette.glow.copy(alpha = 0.35f), Color.Transparent), center = Offset(w / 2, horizon), radius = w * 0.45f))
        // Skyline (far away, so it stays still).
        val n = buildings.size
        val bw = w / n
        buildings.forEachIndexed { i, v ->
            val centre = 1f - kotlin.math.abs(i - n / 2f) / (n / 2f)
            val bh = horizon * (0.18f + 0.55f * v * (0.4f + 0.6f * centre))
            val left = i * bw + bw * 0.08f
            drawRect(Color(0xFF0A1A36), Offset(left, horizon - bh), Size(bw * 0.84f, bh))
            drawRect(Brush.verticalGradient(listOf(palette.accent.copy(alpha = 0.08f), Color.Transparent), startY = horizon - bh, endY = horizon), Offset(left, horizon - bh), Size(bw * 0.84f, bh))
        }
        windows.forEach { (x, y, a) ->
            val bi = (x * n).toInt().coerceIn(0, n - 1)
            val centre = 1f - kotlin.math.abs(bi - n / 2f) / (n / 2f)
            val bh = horizon * (0.18f + 0.55f * buildings[bi] * (0.4f + 0.6f * centre))
            val wy = horizon - bh * (0.05f + 0.9f * y)
            val wx = bi * bw + bw * (0.2f + 0.6f * ((x * n) % 1f))
            if (a > 0.45f) drawRect((if (a > 0.9f) Color(0xFFFFE8A8) else palette.accent).copy(alpha = 0.25f + 0.55f * (a - 0.45f)), Offset(wx, wy), Size(w * 0.004f, w * 0.006f))
        }
        // Ground and road.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF050B17), Color(0xFF02050C)), startY = horizon), Offset(0f, horizon), Size(w, h - horizon))
        val road = Path().apply {
            moveTo(w * 0.48f, horizon); lineTo(w * 0.52f, horizon); lineTo(w * 0.80f, h); lineTo(w * 0.20f, h); close()
        }
        drawPath(road, Brush.verticalGradient(listOf(Color(0xFF0E1A30), Color(0xFF16233C)), startY = horizon, endY = h))
        // Glowing road edges.
        drawLine(palette.accent.copy(alpha = 0.8f), Offset(w * 0.48f, horizon), Offset(w * 0.20f, h), strokeWidth = 2.dp.toPx())
        drawLine(palette.accent2.copy(alpha = 0.8f), Offset(w * 0.52f, horizon), Offset(w * 0.80f, h), strokeWidth = 2.dp.toPx())

        // Everything below moves with the distance actually driven.
        // depth t: 0 at the horizon, 1 at the bottom edge; perspective y = horizon + (h - horizon) * t².
        fun yAt(t: Float) = horizon + (h - horizon) * t.pow(2f)
        fun xAt(lane: Float, t: Float) = w / 2 + lane * (w * 0.02f + w * 0.28f * t.pow(2f)) // lane: -1 left edge … 1 right edge
        val dashPhase = (travelled / 9f) % 1f           // a lane dash every 9 m
        val polePhase = (travelled / 25f) % 1f          // a road light every 25 m
        val dashes = 6
        for (lane in listOf(-0.33f, 0.33f)) {
            for (i in 0 until dashes) {
                val t0 = (i + dashPhase) / dashes
                val t1 = (t0 + 0.06f).coerceAtMost(1f)
                drawLine(
                    Color.White.copy(alpha = 0.12f + 0.55f * t0),
                    Offset(xAt(lane, t0), yAt(t0)), Offset(xAt(lane, t1), yAt(t1)),
                    strokeWidth = (0.8f + 3.5f * t0).dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        // Road lights passing on both sides.
        val poles = 4
        for (i in 0 until poles) {
            val t = (i + polePhase) / poles
            val y = yAt(t)
            val hgt = (h - horizon) * 0.10f + h * 0.55f * t.pow(2f)
            for (side in listOf(-1f, 1f)) {
                val x = xAt(side * 1.08f, t)
                drawLine(Color(0xFF26354F).copy(alpha = 0.3f + 0.6f * t), Offset(x, y), Offset(x, y - hgt), strokeWidth = (0.6f + 2.5f * t).dp.toPx())
                val lamp = Offset(x - side * hgt * 0.12f, y - hgt)
                drawCircle(Brush.radialGradient(listOf(palette.accent.copy(alpha = 0.55f * t), Color.Transparent), center = lamp, radius = 4.dp.toPx() + 16.dp.toPx() * t), radius = 4.dp.toPx() + 16.dp.toPx() * t, center = lamp)
                drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * t), radius = 0.8.dp.toPx() + 2.5.dp.toPx() * t, center = lamp)
            }
        }
        // Speed streaks once really moving.
        val streak = ((speed - 40f) / 100f).coerceIn(0f, 1f)
        if (streak > 0f) {
            for (k in 0 until 6) {
                val t = ((k / 6f) + (travelled / 6f) % 1f / 6f * 6f) % 1f
                val side = if (k % 2 == 0) -1f else 1f
                val x0 = xAt(side * 0.75f, t)
                val x1 = xAt(side * 0.75f, (t + 0.18f).coerceAtMost(1f))
                drawLine(palette.accent.copy(alpha = 0.35f * streak * t), Offset(x0, yAt(t)), Offset(x1, yAt((t + 0.18f).coerceAtMost(1f))), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        // Slight body motion while driving.
        val bob = if (speed > 1f) kotlin.math.sin(travelled * 2.1f) * h * 0.003f * (speed / 120f).coerceAtMost(1f) else 0f
        drawCarRear(Offset(w / 2, h * 0.74f + bob), w * 0.21f, palette, braking || speedKmh < 0.5f)
    }
}

/** Original stylised SUV seen from behind, driving into the scene. */
private fun DrawScope.drawCarRear(c: Offset, cw: Float, p: DashPalette, brakesOn: Boolean) {
    val ch = cw * 0.66f
    val l = c.x - cw / 2
    val t = c.y - ch / 2
    val tail = Color(0xFFFF2A2A)
    // Ground glow (accent) and red tail-light reflection on the road.
    drawOval(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.40f), Color.Transparent), center = Offset(c.x, t + ch), radius = cw * 0.75f), Offset(l - cw * 0.25f, t + ch * 0.82f), Size(cw * 1.5f, ch * 0.4f))
    drawOval(Brush.radialGradient(listOf(tail.copy(alpha = if (brakesOn) 0.45f else 0.22f), Color.Transparent), center = Offset(c.x, t + ch * 1.02f), radius = cw * 0.6f), Offset(l - cw * 0.1f, t + ch * 0.9f), Size(cw * 1.2f, ch * 0.3f))
    drawOval(Color.Black.copy(alpha = 0.65f), Offset(l - cw * 0.02f, t + ch * 0.90f), Size(cw * 1.04f, ch * 0.14f))
    // Tyres.
    drawRoundRect(Color(0xFF080B11), Offset(l + cw * 0.03f, t + ch * 0.74f), Size(cw * 0.16f, ch * 0.24f), CornerRadius(cw * 0.035f))
    drawRoundRect(Color(0xFF080B11), Offset(l + cw * 0.81f, t + ch * 0.74f), Size(cw * 0.16f, ch * 0.24f), CornerRadius(cw * 0.035f))
    // Roof and rear glass.
    val cabin = Path().apply {
        moveTo(l + cw * 0.12f, t + ch * 0.44f)
        lineTo(l + cw * 0.20f, t + ch * 0.10f)
        quadraticTo(l + cw * 0.5f, t + ch * 0.03f, l + cw * 0.80f, t + ch * 0.10f)
        lineTo(l + cw * 0.88f, t + ch * 0.44f)
        close()
    }
    drawPath(cabin, Brush.verticalGradient(listOf(Color(0xFFDCE3EC), Color(0xFFF2F5FA)), startY = t, endY = t + ch * 0.44f))
    val glass = Path().apply {
        moveTo(l + cw * 0.19f, t + ch * 0.40f)
        lineTo(l + cw * 0.25f, t + ch * 0.17f)
        quadraticTo(l + cw * 0.5f, t + ch * 0.12f, l + cw * 0.75f, t + ch * 0.17f)
        lineTo(l + cw * 0.81f, t + ch * 0.40f)
        close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF223652), Color(0xFF0A1220)), startY = t + ch * 0.12f, endY = t + ch * 0.40f))
    drawLine(Color.White.copy(alpha = 0.18f), Offset(l + cw * 0.62f, t + ch * 0.17f), Offset(l + cw * 0.70f, t + ch * 0.38f), strokeWidth = cw * 0.012f)
    // Roof spoiler with the high-mounted stop light.
    drawRoundRect(Color(0xFF1A2232), Offset(l + cw * 0.22f, t + ch * 0.075f), Size(cw * 0.56f, ch * 0.05f), CornerRadius(ch * 0.025f))
    drawLine(tail.copy(alpha = if (brakesOn) 1f else 0.35f), Offset(c.x - cw * 0.08f, t + ch * 0.10f), Offset(c.x + cw * 0.08f, t + ch * 0.10f), strokeWidth = ch * 0.018f, cap = StrokeCap.Round)
    // Tailgate / lower body.
    val body = Path().apply {
        moveTo(l + cw * 0.05f, t + ch * 0.44f)
        quadraticTo(l + cw * 0.5f, t + ch * 0.40f, l + cw * 0.95f, t + ch * 0.44f)
        quadraticTo(l + cw * 1.0f, t + ch * 0.48f, l + cw * 0.99f, t + ch * 0.64f)
        lineTo(l + cw * 0.97f, t + ch * 0.86f)
        quadraticTo(l + cw * 0.96f, t + ch * 0.92f, l + cw * 0.88f, t + ch * 0.92f)
        lineTo(l + cw * 0.12f, t + ch * 0.92f)
        quadraticTo(l + cw * 0.04f, t + ch * 0.92f, l + cw * 0.03f, t + ch * 0.86f)
        lineTo(l + cw * 0.01f, t + ch * 0.64f)
        quadraticTo(l, t + ch * 0.48f, l + cw * 0.05f, t + ch * 0.44f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFF4F7FB), Color(0xFFB9C3D1)), startY = t + ch * 0.42f, endY = t + ch * 0.92f))
    // Full-width LED tail-light bar with glow (brighter while braking).
    val barY = t + ch * 0.52f
    val glowA = if (brakesOn) 0.55f else 0.28f
    drawLine(tail.copy(alpha = glowA), Offset(l + cw * 0.06f, barY), Offset(l + cw * 0.94f, barY), strokeWidth = ch * 0.14f, cap = StrokeCap.Round)
    drawLine(Color(0xFF3A0A0E), Offset(l + cw * 0.06f, barY), Offset(l + cw * 0.94f, barY), strokeWidth = ch * 0.06f, cap = StrokeCap.Round)
    drawLine(tail.copy(alpha = if (brakesOn) 1f else 0.75f), Offset(l + cw * 0.08f, barY), Offset(l + cw * 0.92f, barY), strokeWidth = ch * 0.022f, cap = StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val x0 = c.x + side * cw * 0.30f
        val x1 = c.x + side * cw * 0.46f
        drawLine(tail, Offset(x0, barY), Offset(x1, barY + ch * 0.02f), strokeWidth = ch * 0.05f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFFC2C2), Offset(x0 + side * cw * 0.02f, barY), Offset(x1 - side * cw * 0.02f, barY + ch * 0.02f), strokeWidth = ch * 0.012f, cap = StrokeCap.Round)
    }
    // Badge area, plate, bumper, reflectors, diffuser.
    drawRoundRect(Color(0xFF9AA6B8), Offset(c.x - cw * 0.05f, t + ch * 0.59f), Size(cw * 0.10f, ch * 0.025f), CornerRadius(ch * 0.01f))
    drawRoundRect(Color(0xFFE9EEF5), Offset(c.x - cw * 0.12f, t + ch * 0.655f), Size(cw * 0.24f, ch * 0.08f), CornerRadius(ch * 0.01f))
    drawRoundRect(Color(0xFF2A3446), Offset(c.x - cw * 0.12f, t + ch * 0.655f), Size(cw * 0.24f, ch * 0.08f), CornerRadius(ch * 0.01f), style = Stroke(ch * 0.008f))
    drawRoundRect(Color(0xFF151C28), Offset(l + cw * 0.08f, t + ch * 0.78f), Size(cw * 0.84f, ch * 0.10f), CornerRadius(ch * 0.04f))
    for (side in listOf(-1f, 1f)) {
        drawLine(tail.copy(alpha = 0.7f), Offset(c.x + side * cw * 0.34f, t + ch * 0.80f), Offset(c.x + side * cw * 0.42f, t + ch * 0.80f), strokeWidth = ch * 0.015f, cap = StrokeCap.Round)
    }
    drawLine(Color(0xFF8A96A8), Offset(c.x - cw * 0.22f, t + ch * 0.855f), Offset(c.x + cw * 0.22f, t + ch * 0.855f), strokeWidth = ch * 0.012f, cap = StrokeCap.Round)
}

/** Simple engine outline icon (no engine glyph exists in the Material set). */
val EngineIcon: ImageVector by lazy {
    ImageVector.Builder("engine", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(7f, 4f); lineTo(13f, 4f); lineTo(13f, 6f); lineTo(11f, 6f); lineTo(11f, 7f); lineTo(16f, 7f)
            lineTo(18f, 9f); lineTo(20f, 9f); lineTo(20f, 7f); lineTo(22f, 7f); lineTo(22f, 16f); lineTo(20f, 16f)
            lineTo(20f, 14f); lineTo(18f, 14f); lineTo(18f, 17f); lineTo(16f, 19f); lineTo(8f, 19f); lineTo(6f, 16f)
            lineTo(4f, 16f); lineTo(4f, 18f); lineTo(2f, 18f); lineTo(2f, 9f); lineTo(4f, 9f); lineTo(4f, 11f)
            lineTo(6f, 11f); lineTo(6f, 8f); lineTo(9f, 7f); lineTo(9f, 6f); lineTo(7f, 6f); close()
            moveTo(8f, 9f); lineTo(16f, 9f); lineTo(16f, 16f); lineTo(9f, 16f); lineTo(8f, 14f); close()
        }
    }.build()
}
