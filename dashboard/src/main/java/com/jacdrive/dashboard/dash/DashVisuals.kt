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
    val roadPhase = (travelled / 9f) % 1f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val vx = w / 2
        val horizon = h * 0.22f
        // Horizon glow (elliptical, fades out before the top edge) and the bright horizon line.
        drawOval(
            Brush.radialGradient(listOf(DashPalette.Blue.copy(alpha = 0.55f), Color.Transparent), center = Offset(vx, horizon), radius = w * 0.5f),
            topLeft = Offset(vx - w * 0.5f, horizon - h * 0.22f), size = Size(w, h * 0.5f),
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, palette.accent.copy(alpha = 0.9f), Color.Transparent)),
            topLeft = Offset(0f, horizon - 1.dp.toPx()), size = Size(w, 2.dp.toPx()),
        )
        // Ground fading into the page.
        drawOval(
            Brush.radialGradient(listOf(Color(0xCC06102A), Color.Transparent), center = Offset(vx, horizon), radius = w * 0.55f),
            topLeft = Offset(vx - w * 0.55f, horizon - (h - horizon)), size = Size(w * 1.1f, (h - horizon) * 2f),
        )
        // Road surface.
        val road = Path().apply {
            moveTo(vx - w * 0.03f, horizon); lineTo(vx + w * 0.03f, horizon); lineTo(vx + w * 0.30f, h); lineTo(vx - w * 0.30f, h); close()
        }
        drawPath(road, Brush.verticalGradient(listOf(Color(0xFF10203E), Color(0x99142644)), startY = horizon, endY = h))
        // Light trails spreading to the sides, with highlights that run along them at the real speed.
        val trails = listOf(0.80f, 0.48f, 0.30f)
        val moving = speed > 1f
        trails.forEachIndexed { i, ty ->
            for (side in listOf(-1f, 1f)) {
                val start = Offset(vx + side * w * 0.04f, horizon + h * 0.02f)
                val end = Offset(vx + side * w * (0.56f + i * 0.02f), horizon + (h - horizon) * ty)
                drawLine(
                    Brush.linearGradient(listOf(palette.accent.copy(alpha = 0f), palette.accent.copy(alpha = 0.9f - i * 0.22f)), start, end),
                    start, end, strokeWidth = (2.6f - i * 0.6f).dp.toPx(), cap = StrokeCap.Round,
                )
                if (moving) {
                    val m = ((travelled / (8f + i * 3f)) + i * 0.3f) % 1f
                    val t0 = m * m
                    val t1 = (m + 0.12f).coerceAtMost(1f).let { it * it }
                    drawLine(
                        Color(0xFFC8F0FF).copy(alpha = 0.85f * m),
                        Offset(start.x + (end.x - start.x) * t0, start.y + (end.y - start.y) * t0),
                        Offset(start.x + (end.x - start.x) * t1, start.y + (end.y - start.y) * t1),
                        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }
        // Road edges and moving lane marks.
        drawLine(palette.accent.copy(alpha = 0.85f), Offset(vx - w * 0.03f, horizon), Offset(vx - w * 0.30f, h), strokeWidth = 1.5.dp.toPx())
        drawLine(palette.accent2.copy(alpha = 0.85f), Offset(vx + w * 0.03f, horizon), Offset(vx + w * 0.30f, h), strokeWidth = 1.5.dp.toPx())
        fun yAt(t: Float) = horizon + (h - horizon) * t * t
        for (lane in listOf(-0.33f, 0.33f)) {
            fun xAt(t: Float) = vx + lane * (w * 0.03f + w * 0.27f * t * t)
            for (i in 0 until 5) {
                val t0 = (i + roadPhase) / 5
                val t1 = (t0 + 0.07f).coerceAtMost(1f)
                drawLine(
                    Color.White.copy(alpha = 0.15f + 0.6f * t0),
                    Offset(xAt(t0), yAt(t0)), Offset(xAt(t1), yAt(t1)),
                    strokeWidth = (0.8f + 3f * t0).dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
        // The car, with slight body motion while driving.
        val bob = if (moving) kotlin.math.sin(travelled * 2.1f) * h * 0.006f * (speed / 120f).coerceAtMost(1f) else 0f
        val cw = minOf(w * 0.30f, h * 0.95f)
        drawCarRear(Offset(vx, h - cw * 0.34f + bob), cw, palette, braking || speedKmh < 0.5f)
    }
}

/**
 * Liftback sedan seen from behind, modelled on the JAC J7's proportions: low fastback roof with a
 * long rear screen, silver C-pillar trim, ducktail lip, full-width LED light bar with boomerang
 * clusters, bumper reflectors and a single exhaust. No manufacturer logo is drawn.
 */
private fun DrawScope.drawCarRear(c: Offset, cw: Float, p: DashPalette, brakesOn: Boolean) {
    val ch = cw * 0.60f
    val l = c.x - cw / 2
    val t = c.y - ch / 2
    fun x(f: Float) = l + cw * f
    fun y(f: Float) = t + ch * f
    val tail = Color(0xFFFF2236)
    val lit = if (brakesOn) 1f else 0.78f
    val paintTop = Color(0xFF8C97A8)
    val paintMid = Color(0xFF4A5363)
    val paintLow = Color(0xFF262C37)

    // Ground: accent glow, red light on the road, shadow.
    drawOval(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.40f), Color.Transparent), center = Offset(c.x, y(1f)), radius = cw * 0.75f), Offset(x(-0.25f), y(0.82f)), Size(cw * 1.5f, ch * 0.4f))
    drawOval(Brush.radialGradient(listOf(tail.copy(alpha = if (brakesOn) 0.45f else 0.22f), Color.Transparent), center = Offset(c.x, y(1.02f)), radius = cw * 0.6f), Offset(x(-0.1f), y(0.9f)), Size(cw * 1.2f, ch * 0.3f))
    drawOval(Color.Black.copy(alpha = 0.7f), Offset(x(-0.02f), y(0.90f)), Size(cw * 1.04f, ch * 0.14f))
    // Tyres.
    drawRoundRect(Color(0xFF07090E), Offset(x(0.04f), y(0.76f)), Size(cw * 0.15f, ch * 0.22f), CornerRadius(cw * 0.03f))
    drawRoundRect(Color(0xFF07090E), Offset(x(0.81f), y(0.76f)), Size(cw * 0.15f, ch * 0.22f), CornerRadius(cw * 0.03f))

    // Body: narrow fastback cabin flowing into wide rear haunches.
    val body = Path().apply {
        moveTo(x(0.30f), y(0.04f))
        quadraticTo(x(0.5f), y(0.0f), x(0.70f), y(0.04f))           // roof
        quadraticTo(x(0.86f), y(0.20f), x(0.95f), y(0.38f))          // right C-pillar
        quadraticTo(x(1.01f), y(0.46f), x(0.995f), y(0.62f))         // right haunch
        lineTo(x(0.975f), y(0.86f))
        quadraticTo(x(0.965f), y(0.92f), x(0.89f), y(0.92f))
        lineTo(x(0.11f), y(0.92f))
        quadraticTo(x(0.035f), y(0.92f), x(0.025f), y(0.86f))
        lineTo(x(0.005f), y(0.62f))
        quadraticTo(x(-0.01f), y(0.46f), x(0.05f), y(0.38f))         // left haunch
        quadraticTo(x(0.14f), y(0.20f), x(0.30f), y(0.04f))          // left C-pillar
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(paintTop, paintMid, paintLow), startY = y(0f), endY = y(0.92f)))
    // Accent rim light on the body edge so the dark paint reads on a dark scene.
    drawPath(body, Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.55f), Color.Transparent), startY = y(0f), endY = y(0.7f)), style = Stroke(cw * 0.006f))

    // Long, raked rear screen.
    val glass = Path().apply {
        moveTo(x(0.33f), y(0.075f))
        quadraticTo(x(0.5f), y(0.045f), x(0.67f), y(0.075f))
        lineTo(x(0.84f), y(0.33f))
        quadraticTo(x(0.5f), y(0.36f), x(0.16f), y(0.33f))
        close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF2A3B57), Color(0xFF0B111C)), startY = y(0.05f), endY = y(0.35f)))
    drawLine(Color.White.copy(alpha = 0.16f), Offset(x(0.60f), y(0.09f)), Offset(x(0.70f), y(0.31f)), strokeWidth = cw * 0.012f)
    // Roof spoiler at the top of the screen and shark-fin antenna.
    drawLine(Color(0xFF0D121B), Offset(x(0.34f), y(0.07f)), Offset(x(0.66f), y(0.07f)), strokeWidth = ch * 0.03f, cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(x(0.485f), y(0.03f)); lineTo(x(0.5f), y(-0.025f)); lineTo(x(0.515f), y(0.03f)); close() }, Color(0xFF151B26))
    // Silver C-pillar trim sweeping down to the trunk lip.
    val trimColor = Color(0xFFE4E9F0)
    for (side in listOf(-1f, 1f)) {
        val sx = { f: Float -> c.x + side * cw * f }
        drawPath(
            Path().apply { moveTo(sx(0.21f), y(0.045f)); quadraticTo(sx(0.36f), y(0.16f), sx(0.44f), y(0.36f)) },
            trimColor, style = Stroke(ch * 0.016f, cap = StrokeCap.Round),
        )
    }
    // Ducktail lip across the trunk.
    drawPath(
        Path().apply { moveTo(x(0.08f), y(0.385f)); quadraticTo(x(0.5f), y(0.355f), x(0.92f), y(0.385f)) },
        Color(0xFF0D121B), style = Stroke(ch * 0.025f, cap = StrokeCap.Round),
    )
    drawPath(
        Path().apply { moveTo(x(0.12f), y(0.37f)); quadraticTo(x(0.5f), y(0.343f), x(0.88f), y(0.37f)) },
        trimColor.copy(alpha = 0.7f), style = Stroke(ch * 0.008f, cap = StrokeCap.Round),
    )

    // Full-width LED light bar with boomerang clusters at the ends.
    val barY = y(0.47f)
    drawLine(tail.copy(alpha = if (brakesOn) 0.5f else 0.25f), Offset(x(0.06f), barY), Offset(x(0.94f), barY), strokeWidth = ch * 0.12f, cap = StrokeCap.Round)
    drawLine(Color(0xFF2A0710), Offset(x(0.10f), barY), Offset(x(0.90f), barY), strokeWidth = ch * 0.030f, cap = StrokeCap.Round)
    drawLine(tail.copy(alpha = lit), Offset(x(0.12f), barY), Offset(x(0.88f), barY), strokeWidth = ch * 0.012f, cap = StrokeCap.Round)
    for (side in listOf(-1f, 1f)) {
        val sx = { f: Float -> c.x + side * cw * f }
        // Cluster housing: tall at the outer corner, tapering towards the centre.
        val cluster = Path().apply {
            moveTo(sx(0.50f), y(0.41f))
            lineTo(sx(0.47f), y(0.535f))
            lineTo(sx(0.30f), y(0.505f))
            lineTo(sx(0.24f), y(0.485f))
            lineTo(sx(0.24f), y(0.455f))
            lineTo(sx(0.31f), y(0.435f))
            close()
        }
        drawPath(cluster, Color(0xFF3A0610))
        // Outer red lamp.
        val red = Path().apply {
            moveTo(sx(0.49f), y(0.425f)); lineTo(sx(0.465f), y(0.52f)); lineTo(sx(0.39f), y(0.505f)); lineTo(sx(0.40f), y(0.44f)); close()
        }
        drawPath(red, tail.copy(alpha = lit))
        // Clear inner section (reverse lamp area) with red outline.
        val clear = Path().apply {
            moveTo(sx(0.38f), y(0.445f)); lineTo(sx(0.375f), y(0.505f)); lineTo(sx(0.29f), y(0.49f)); lineTo(sx(0.29f), y(0.455f)); close()
        }
        drawPath(clear, Color(0xFFD9DDE4).copy(alpha = 0.85f))
        drawPath(clear, tail.copy(alpha = lit), style = Stroke(ch * 0.008f))
        // Bright LED edge.
        drawLine(Color(0xFFFFC4CA).copy(alpha = lit), Offset(sx(0.48f), y(0.44f)), Offset(sx(0.41f), y(0.45f)), strokeWidth = ch * 0.010f, cap = StrokeCap.Round)
        // Bumper reflectors.
        val refl = Path().apply {
            moveTo(sx(0.46f), y(0.66f)); lineTo(sx(0.45f), y(0.72f)); lineTo(sx(0.34f), y(0.735f)); lineTo(sx(0.34f), y(0.69f)); close()
        }
        drawPath(refl, tail.copy(alpha = 0.75f))
    }
    // Plain badge (no logo), plate recess, diffuser and exhaust.
    drawOval(Color(0xFFCDD4DE), Offset(c.x - cw * 0.045f, barY - ch * 0.022f), Size(cw * 0.09f, ch * 0.044f), style = Stroke(ch * 0.007f))
    drawRoundRect(Color(0xFF1A202B), Offset(c.x - cw * 0.14f, y(0.58f)), Size(cw * 0.28f, ch * 0.13f), CornerRadius(ch * 0.02f))
    drawRoundRect(Color(0xFFE9EEF5), Offset(c.x - cw * 0.12f, y(0.595f)), Size(cw * 0.24f, ch * 0.095f), CornerRadius(ch * 0.01f))
    drawRoundRect(Color(0xFF0C1018), Offset(x(0.10f), y(0.80f)), Size(cw * 0.80f, ch * 0.10f), CornerRadius(ch * 0.04f))
    drawOval(Color(0xFF9AA4B4), Offset(x(0.24f), y(0.83f)), Size(cw * 0.07f, ch * 0.05f))
    drawOval(Color(0xFF05070B), Offset(x(0.25f), y(0.84f)), Size(cw * 0.05f, ch * 0.03f))
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
