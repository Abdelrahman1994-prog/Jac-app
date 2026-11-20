package com.jacdrive.dashboard.dash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
@Composable
fun HeroScene(speedKmh: Float, palette: DashPalette, animate: Boolean, modifier: Modifier = Modifier) {
    val phase = if (animate && speedKmh > 1f) {
        val period = (900_000f / speedKmh.coerceIn(5f, 200f)).toInt().coerceIn(300, 12_000)
        rememberInfiniteTransition(label = "lane").animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "lanePhase").value
    } else 0f
    val windows = remember { Random(7).let { r -> List(260) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val buildings = remember { Random(3).let { r -> List(26) { r.nextFloat() } } }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.52f
        // Sky glow.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF071530), Color(0xFF0B2350), Color(0xFF06101F)), endY = horizon))
        drawRect(Brush.radialGradient(listOf(palette.glow.copy(alpha = 0.35f), Color.Transparent), center = Offset(w / 2, horizon), radius = w * 0.45f))
        // Skyline.
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
        // Road.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF050B17), Color(0xFF02050C)), startY = horizon), Offset(0f, horizon), Size(w, h - horizon))
        val road = Path().apply {
            moveTo(w * 0.47f, horizon); lineTo(w * 0.53f, horizon); lineTo(w * 0.95f, h); lineTo(w * 0.05f, h); close()
        }
        drawPath(road, Brush.verticalGradient(listOf(Color(0xFF0E1A30), Color(0xFF16233C)), startY = horizon, endY = h))
        // Light streaks along the road edges.
        for (k in 0..2) {
            val off = k * 0.035f
            drawLine(
                Brush.horizontalGradient(listOf(Color.Transparent, palette.accent.copy(alpha = 0.7f - k * 0.2f))),
                Offset(w * (0.40f - off * 3), horizon + (h - horizon) * 0.1f), Offset(-w * 0.05f, h * (0.80f + off)), strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                Brush.horizontalGradient(listOf(palette.accent2.copy(alpha = 0.7f - k * 0.2f), Color.Transparent)),
                Offset(w * 1.05f, h * (0.80f + off)), Offset(w * (0.60f + off * 3), horizon + (h - horizon) * 0.1f), strokeWidth = 2.dp.toPx(),
            )
        }
        // Lane marks (move with real speed).
        val dashes = 6
        for (i in 0 until dashes) {
            val t0 = ((i + phase) / dashes)
            val t1 = (t0 + 0.05f).coerceAtMost(1f)
            val y0 = horizon + (h - horizon) * t0.pow(2f)
            val y1 = horizon + (h - horizon) * t1.pow(2f)
            drawLine(Color.White.copy(alpha = 0.15f + 0.5f * t0), Offset(w / 2, y0), Offset(w / 2, y1), strokeWidth = (1f + 4f * t0).dp.toPx())
        }
        drawCarFront(Offset(w / 2, h * 0.70f), w * 0.25f, palette)
    }
}

private fun DrawScope.drawCarFront(c: Offset, cw: Float, p: DashPalette) {
    val ch = cw * 0.62f
    val l = c.x - cw / 2
    val t = c.y - ch / 2
    // Ground glow and shadow.
    drawOval(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.45f), Color.Transparent), center = Offset(c.x, t + ch), radius = cw * 0.75f), Offset(l - cw * 0.25f, t + ch * 0.82f), Size(cw * 1.5f, ch * 0.4f))
    drawOval(Color.Black.copy(alpha = 0.6f), Offset(l - cw * 0.02f, t + ch * 0.90f), Size(cw * 1.04f, ch * 0.16f))
    // Wheels.
    drawRoundRect(Color(0xFF0B0F16), Offset(l + cw * 0.02f, t + ch * 0.72f), Size(cw * 0.16f, ch * 0.26f), CornerRadius(cw * 0.04f))
    drawRoundRect(Color(0xFF0B0F16), Offset(l + cw * 0.82f, t + ch * 0.72f), Size(cw * 0.16f, ch * 0.26f), CornerRadius(cw * 0.04f))
    // Lower body.
    val body = Path().apply {
        moveTo(l + cw * 0.06f, t + ch * 0.42f)
        quadraticTo(l + cw * 0.5f, t + ch * 0.36f, l + cw * 0.94f, t + ch * 0.42f)
        quadraticTo(l + cw * 1.0f, t + ch * 0.46f, l + cw * 0.99f, t + ch * 0.62f)
        lineTo(l + cw * 0.97f, t + ch * 0.86f)
        quadraticTo(l + cw * 0.96f, t + ch * 0.92f, l + cw * 0.88f, t + ch * 0.92f)
        lineTo(l + cw * 0.12f, t + ch * 0.92f)
        quadraticTo(l + cw * 0.04f, t + ch * 0.92f, l + cw * 0.03f, t + ch * 0.86f)
        lineTo(l + cw * 0.01f, t + ch * 0.62f)
        quadraticTo(l, t + ch * 0.46f, l + cw * 0.06f, t + ch * 0.42f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFF4F7FB), Color(0xFFBCC6D3)), startY = t + ch * 0.38f, endY = t + ch * 0.92f))
    // Greenhouse + windscreen.
    val cabin = Path().apply {
        moveTo(l + cw * 0.14f, t + ch * 0.42f)
        lineTo(l + cw * 0.24f, t + ch * 0.12f)
        quadraticTo(l + cw * 0.5f, t + ch * 0.04f, l + cw * 0.76f, t + ch * 0.12f)
        lineTo(l + cw * 0.86f, t + ch * 0.42f)
        close()
    }
    drawPath(cabin, Brush.verticalGradient(listOf(Color(0xFFE6ECF4), Color(0xFFF4F7FB)), startY = t, endY = t + ch * 0.42f))
    val glass = Path().apply {
        moveTo(l + cw * 0.20f, t + ch * 0.39f)
        lineTo(l + cw * 0.27f, t + ch * 0.16f)
        quadraticTo(l + cw * 0.5f, t + ch * 0.09f, l + cw * 0.73f, t + ch * 0.16f)
        lineTo(l + cw * 0.80f, t + ch * 0.39f)
        close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF1B2C46), Color(0xFF0A1220)), startY = t + ch * 0.1f, endY = t + ch * 0.4f))
    drawLine(Color.White.copy(alpha = 0.25f), Offset(l + cw * 0.32f, t + ch * 0.18f), Offset(l + cw * 0.27f, t + ch * 0.36f), strokeWidth = cw * 0.01f)
    // Mirrors.
    drawRoundRect(Color(0xFFDDE4EE), Offset(l - cw * 0.04f, t + ch * 0.34f), Size(cw * 0.09f, ch * 0.08f), CornerRadius(cw * 0.02f))
    drawRoundRect(Color(0xFFDDE4EE), Offset(l + cw * 0.95f, t + ch * 0.34f), Size(cw * 0.09f, ch * 0.08f), CornerRadius(cw * 0.02f))
    // Grille.
    val grille = Path().apply {
        moveTo(l + cw * 0.30f, t + ch * 0.55f); lineTo(l + cw * 0.70f, t + ch * 0.55f)
        lineTo(l + cw * 0.66f, t + ch * 0.72f); lineTo(l + cw * 0.34f, t + ch * 0.72f); close()
    }
    drawPath(grille, Color(0xFF111824))
    for (i in 1..4) {
        val y = t + ch * (0.55f + 0.034f * i)
        drawLine(Color(0xFF8A96A8).copy(alpha = 0.6f), Offset(l + cw * (0.31f + 0.008f * i), y), Offset(l + cw * (0.69f - 0.008f * i), y), strokeWidth = cw * 0.005f)
    }
    // LED headlights with glow.
    for (side in listOf(-1f, 1f)) {
        val x0 = c.x + side * cw * 0.20f
        val x1 = c.x + side * cw * 0.44f
        val y0 = t + ch * 0.53f
        val y1 = t + ch * 0.49f
        drawLine(p.glow.copy(alpha = 0.25f), Offset(x0, y0), Offset(x1, y1), strokeWidth = ch * 0.10f, cap = StrokeCap.Round)
        drawLine(p.accent, Offset(x0, y0), Offset(x1, y1), strokeWidth = ch * 0.035f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(x0 + side * cw * 0.03f, y0 - ch * 0.004f), Offset(x1 - side * cw * 0.03f, y1 + ch * 0.004f), strokeWidth = ch * 0.012f, cap = StrokeCap.Round)
    }
    // Lower intake and plate.
    drawRoundRect(Color(0xFF0E141F), Offset(l + cw * 0.22f, t + ch * 0.78f), Size(cw * 0.56f, ch * 0.08f), CornerRadius(ch * 0.03f))
    drawRoundRect(Color(0xFFE9EEF5), Offset(c.x - cw * 0.11f, t + ch * 0.74f), Size(cw * 0.22f, ch * 0.075f), CornerRadius(ch * 0.01f))
    drawRoundRect(Color(0xFF2A3446), Offset(c.x - cw * 0.11f, t + ch * 0.74f), Size(cw * 0.22f, ch * 0.075f), CornerRadius(ch * 0.01f), style = Stroke(ch * 0.008f))
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
