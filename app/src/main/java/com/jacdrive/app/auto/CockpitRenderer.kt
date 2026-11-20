package com.jacdrive.app.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Values to draw, already converted to the user's units. Null = no data. */
data class CockpitFrame(
    val speed: Float?, val speedMax: Float, val speedUnit: String,
    val rpm: Float?, val redline: Float,
    val coolant: Float?, val coolantUnit: String, val coolantHot: Boolean,
    val battery: Float?, val batteryBad: Boolean,
    val fuel: Float?, val load: Float?,
    val status: String, val demo: Boolean, val milOn: Boolean,
    val labels: Labels,
    val throttle: Float? = null,
    val intake: Float? = null,
    val trip: String? = null,
    val avgFuel: String? = null,
    /** 0..1 position of the moving lane marks; advanced with real vehicle speed. */
    val roadPhase: Float = 0f,
    /** Metres travelled (from real speed); moves road lights and streaks. */
    val travelled: Float = 0f,
    /** Real speed dropping fast or car stopped: brake lights bright. */
    val braking: Boolean = false,
) {
    data class Labels(
        val rpm: String, val coolant: String, val battery: String, val fuel: String, val load: String,
        val demo: String, val mil: String, val throttle: String = "Throttle", val intake: String = "Intake",
        val trip: String = "Trip", val avgFuel: String = "Avg",
    )
}

/**
 * Draws the full cockpit with android.graphics for the Android Auto map surface: two main dials,
 * a perspective road whose lane marks move with real vehicle speed, and six mini gauges.
 * Large numerals, no blinking. Pure drawing — testable on a Bitmap.
 */
class CockpitRenderer(private val bold: Typeface?, private val light: Typeface?) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    // Palette matched to the JAC J7 instrument cluster: deep navy, electric cyan, white car.
    private val accent = Color.rgb(64, 208, 255)
    private val accentDeep = Color.rgb(28, 108, 255)
    private val numeral = Color.rgb(112, 222, 255)
    private val track = Color.rgb(16, 40, 74)
    private val amber = Color.rgb(255, 176, 32)
    private val red = Color.rgb(255, 70, 88)
    private val dim = Color.rgb(112, 152, 196)
    private val purple = Color.rgb(181, 123, 255)

    private data class Mini(val label: String, val value: String?, val fraction: Float?, val warn: Int?)

    fun draw(c: Canvas, area: Rect, f: CockpitFrame) {
        background(c, area)
        val w = area.width().toFloat()
        val h = area.height().toFloat()
        if (h > w * 1.05f) drawPortrait(c, area, f, w, h) else drawLandscape(c, area, f, w, h)
    }

    /** Navy cluster background: vertical gradient, central glow, curved light rails at the edges. */
    private fun background(c: Canvas, area: Rect) {
        val cw = c.width.toFloat()
        val chh = c.height.toFloat()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, 0f, chh, Color.rgb(11, 30, 64), Color.rgb(3, 8, 20), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, cw, chh, p)
        p.shader = RadialGradient(area.exactCenterX(), area.top + area.height() * 0.55f, max(cw, chh) * 0.55f,
            Color.argb(70, 28, 108, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, cw, chh, p)
        // Curved light rails (like the cluster bezel), top and bottom.
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        val w = area.width().toFloat()
        val h = area.height().toFloat()
        for ((y, bend) in listOf(area.top + h * 0.004f to h * 0.012f, area.bottom - h * 0.004f to -h * 0.012f)) {
            val path = Path().apply {
                moveTo(area.left + w * 0.06f, y + bend)
                quadTo(area.exactCenterX(), y - bend, area.right - w * 0.06f, y + bend)
            }
            p.strokeWidth = h * 0.006f
            p.shader = LinearGradient(area.left.toFloat(), 0f, area.right.toFloat(), 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(200, 64, 208, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.shader = null
    }

    private fun minis(f: CockpitFrame) = listOf(
        Mini(f.labels.coolant, f.coolant?.let { "%.0f%s".format(it, f.coolantUnit) }, f.coolant?.let { if (f.coolantUnit == "°C") (it - 40) / 90 else (it - 104) / 162 }, if (f.coolantHot) red else null),
        Mini(f.labels.battery, f.battery?.let { "%.1f V".format(it) }, f.battery?.let { (it - 10) / 6 }, if (f.batteryBad) amber else null),
        Mini(f.labels.fuel, f.fuel?.let { "%.0f%%".format(it) }, f.fuel?.div(100), if ((f.fuel ?: 100f) < 10f) amber else null),
        Mini(f.labels.load, f.load?.let { "%.0f%%".format(it) }, f.load?.div(100), null),
        Mini(f.labels.throttle, f.throttle?.let { "%.0f%%".format(it) }, f.throttle?.div(100), null),
        Mini(f.labels.intake, f.intake?.let { "%.0f%s".format(it, f.coolantUnit) }, f.intake?.let { if (f.coolantUnit == "°C") (it + 10) / 90 else (it - 14) / 162 }, null),
    )

    private fun header(c: Canvas, cx: Float, y: Float, size: Float, f: CockpitFrame) {
        text(c, f.status, cx, y, size, if (f.demo) purple else dim, bold, Paint.Align.CENTER)
        if (f.milOn) text(c, f.labels.mil, cx, y + size * 1.3f, size * 1.1f, red, bold, Paint.Align.CENTER)
    }

    private fun footer(c: Canvas, cx: Float, y: Float, size: Float, f: CockpitFrame) {
        val parts = listOfNotNull(
            f.trip?.let { "${f.labels.trip} $it" },
            f.avgFuel?.let { "${f.labels.avgFuel} $it" },
            if (f.demo) f.labels.demo else null,
        )
        if (parts.isNotEmpty()) text(c, parts.joinToString("   ·   "), cx, y, size, if (f.demo) purple else dim, bold, Paint.Align.CENTER)
    }

    /** Tall screens (the JAC J7's portrait head unit). */
    private fun drawPortrait(c: Canvas, area: Rect, f: CockpitFrame, w: Float, h: Float) {
        val cx = area.exactCenterX()
        header(c, cx, area.top + h * 0.05f, min(w * 0.042f, h * 0.028f), f)

        val d = min(w * 0.47f, h * 0.30f)
        val dialCy = area.top + h * (if (f.milOn) 0.10f else 0.08f) + d / 2
        speedDial(c, area.left + w * 0.25f, dialCy, d, f)
        rpmDial(c, area.left + w * 0.75f, dialCy, d, f)

        val roadTop = dialCy + d / 2 + h * 0.005f
        val roadH = h * 0.22f
        road(c, RectF(area.left + w * 0.06f, roadTop, area.right - w * 0.06f, roadTop + roadH), f)

        val gridTop = roadTop + roadH + h * 0.02f
        val footerH = h * 0.05f
        val gridBottom = area.bottom - footerH
        val cols = 3
        val rows = 2
        val cellW = w / cols
        val cellH = (gridBottom - gridTop) / rows
        minis(f).forEachIndexed { i, m ->
            val col = i % cols
            val row = i / cols
            miniGauge(c, RectF(area.left + col * cellW, gridTop + row * cellH, area.left + (col + 1) * cellW, gridTop + (row + 1) * cellH), m, f.demo)
        }
        footer(c, cx, area.bottom - footerH * 0.35f, min(w * 0.036f, h * 0.024f), f)
    }

    private fun drawLandscape(c: Canvas, area: Rect, f: CockpitFrame, w: Float, h: Float) {
        val d = min(w * 0.29f, h * 0.82f)
        val cy = area.top + h * 0.50f
        val leftCx = area.left + w * 0.035f + d / 2
        val rightCx = area.right - w * 0.035f - d / 2
        speedDial(c, leftCx, cy, d, f)
        rpmDial(c, rightCx, cy, d, f)

        val colL = leftCx + d / 2 + w * 0.015f
        val colR = rightCx - d / 2 - w * 0.015f
        val colW = colR - colL
        header(c, (colL + colR) / 2, area.top + h * 0.07f, h * 0.04f, f)
        val roadTop = area.top + h * (if (f.milOn) 0.15f else 0.11f)
        val roadBottom = area.top + h * 0.50f
        road(c, RectF(colL, roadTop, colR, roadBottom), f)

        val gridTop = roadBottom + h * 0.02f
        val gridBottom = area.bottom - h * 0.07f
        val cellW = colW / 3
        val cellH = (gridBottom - gridTop) / 2
        minis(f).forEachIndexed { i, m ->
            val col = i % 3
            val row = i / 3
            miniGauge(c, RectF(colL + col * cellW, gridTop + row * cellH, colL + (col + 1) * cellW, gridTop + (row + 1) * cellH), m, f.demo)
        }
        footer(c, (colL + colR) / 2, area.bottom - h * 0.025f, h * 0.035f, f)
    }

    private fun speedDial(c: Canvas, cx: Float, cy: Float, d: Float, f: CockpitFrame) =
        dial(c, cx, cy, d, f.speed?.div(f.speedMax), if (f.demo) purple else accent, null, f.speed?.let { "%.0f".format(it) } ?: "—", f.speedUnit)

    private fun rpmDial(c: Canvas, cx: Float, cy: Float, d: Float, f: CockpitFrame) {
        val rpmFrac = f.rpm?.div(8000f)
        val redFrac = f.redline / 8000f
        dial(c, cx, cy, d, rpmFrac, if (rpmFrac != null && rpmFrac >= redFrac) red else if (f.demo) purple else accent, redFrac, f.rpm?.let { "%.1f".format(it / 1000f) } ?: "—", f.labels.rpm)
    }

    private fun dial(c: Canvas, cx: Float, cy: Float, d: Float, fraction: Float?, color: Int, danger: Float?, value: String, unit: String) {
        val stroke = d * 0.045f
        val r = d / 2 - stroke
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val start = 125f
        val sweep = 290f
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = stroke
        p.color = track; c.drawArc(rect, start, sweep, false, p)
        // Fine ticks.
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0..10) {
            val a = Math.toRadians((start + sweep * i / 10).toDouble())
            val r1 = r - stroke * 1.6f
            val r2 = r1 - d * if (i % 2 == 0) 0.05f else 0.025f
            p.color = Color.argb(if (i % 2 == 0) 190 else 100, 150, 215, 255)
            p.strokeWidth = d * if (i % 2 == 0) 0.012f else 0.007f
            c.drawLine(cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1, cx + cos(a).toFloat() * r2, cy + sin(a).toFloat() * r2, p)
        }
        p.strokeWidth = stroke
        if (danger != null) {
            val inner = RectF(rect).apply { inset(stroke * 1.2f, stroke * 1.2f) }
            p.strokeWidth = stroke * 0.35f; p.strokeCap = Paint.Cap.BUTT; p.color = red
            c.drawArc(inner, start + sweep * danger, sweep * (1 - danger), false, p)
            p.strokeWidth = stroke; p.strokeCap = Paint.Cap.ROUND
        }
        val fr = fraction?.coerceIn(0f, 1f)
        if (fr != null && fr > 0.004f) {
            p.color = Color.argb(55, Color.red(color), Color.green(color), Color.blue(color)); p.strokeWidth = stroke * 2.6f
            c.drawArc(rect, start, sweep * fr, false, p)
            p.strokeWidth = stroke
            p.color = color
            c.drawArc(rect, start, sweep * fr, false, p)
            p.shader = null
            val a = Math.toRadians((start + sweep * fr).toDouble())
            p.color = Color.WHITE; p.strokeWidth = stroke * 0.7f
            c.drawLine(cx + cos(a).toFloat() * (r * 0.70f), cy + sin(a).toFloat() * (r * 0.70f), cx + cos(a).toFloat() * (r - stroke), cy + sin(a).toFloat() * (r - stroke), p)
        }
        text(c, value, cx, cy + d * 0.08f, d * 0.25f, if (color == accent) numeral else color, light ?: bold, Paint.Align.CENTER)
        text(c, unit, cx, cy + d * 0.20f, d * 0.065f, dim, bold, Paint.Align.CENTER)
    }

    /** Small 240° arc gauge with the value inside and the label underneath. */
    private fun miniGauge(c: Canvas, cell: RectF, m: Mini, demo: Boolean) {
        val labelSize = min(cell.width() * 0.11f, cell.height() * 0.11f)
        val d = min(cell.width() * 0.78f, (cell.height() - labelSize * 2.2f) * 0.98f)
        val cx = cell.centerX()
        val cy = cell.top + cell.height() * 0.06f + d / 2
        val stroke = d * 0.09f
        val r = d / 2 - stroke
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = stroke
        // Segmented scale (12 bars), lit up to the value.
        val segs = 12
        val seg = 240f / segs
        val fr = m.fraction?.coerceIn(0f, 1f) ?: -1f
        p.strokeCap = Paint.Cap.BUTT
        for (i in 0 until segs) {
            val lit = (i + 0.5f) / segs <= fr
            p.color = when {
                !lit -> track
                m.warn != null -> m.warn
                demo -> purple
                else -> blend(accentDeep, accent, i / (segs - 1f))
            }
            c.drawArc(rect, 150f + i * seg + seg * 0.14f, seg * 0.72f, false, p)
        }
        text(c, m.value ?: "—", cx, cy + d * 0.1f, d * 0.24f, m.warn ?: Color.WHITE, bold, Paint.Align.CENTER)
        text(c, m.label.uppercase(), cx, cy + d / 2 + labelSize * 0.9f, labelSize, dim, bold, Paint.Align.CENTER)
    }

    private val windows = java.util.Random(7).let { r -> List(220) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    private val buildings = java.util.Random(3).let { r -> List(24) { r.nextFloat() } }

    /**
     * Night city, perspective road and the car seen from behind (same scene as the phone dashboard).
     * Lane marks, road lights and streaks move with the real speed. Illustrative only.
     */
    private fun road(c: Canvas, r: RectF, f: CockpitFrame) {
        val w = r.width()
        val h = r.height()
        val vx = r.centerX()
        val horizon = r.top + h * 0.40f
        val radius = min(w, h) * 0.08f
        c.save()
        c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
        p.reset(); p.isAntiAlias = true
        // Sky with a glow on the horizon.
        p.shader = LinearGradient(0f, r.top, 0f, horizon, Color.rgb(7, 21, 48), Color.rgb(11, 35, 80), Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.top, r.right, horizon, p)
        p.shader = RadialGradient(vx, horizon, w * 0.45f, Color.argb(90, 64, 208, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.top, r.right, horizon, p)
        // Skyline with lit windows (static: far away).
        val n = buildings.size
        val bw = w / n
        fun bh(i: Int): Float {
            val centre = 1f - kotlin.math.abs(i - n / 2f) / (n / 2f)
            return (horizon - r.top) * (0.25f + 0.65f * buildings[i] * (0.4f + 0.6f * centre))
        }
        p.shader = null
        for (i in 0 until n) {
            p.color = Color.rgb(10, 26, 54)
            c.drawRect(r.left + i * bw + bw * 0.08f, horizon - bh(i), r.left + i * bw + bw * 0.92f, horizon, p)
        }
        for (wv in windows) {
            val bi = (wv[0] * n).toInt().coerceIn(0, n - 1)
            if (wv[2] <= 0.45f) continue
            val wy = horizon - bh(bi) * (0.05f + 0.9f * wv[1])
            val wx = r.left + bi * bw + bw * (0.2f + 0.6f * ((wv[0] * n) % 1f))
            p.color = if (wv[2] > 0.9f) Color.argb((100 + 120 * wv[2]).toInt(), 255, 232, 168) else Color.argb((60 + 140 * (wv[2] - 0.45f)).toInt(), 64, 208, 255)
            c.drawRect(wx, wy, wx + w * 0.004f, wy + w * 0.006f, p)
        }
        // Ground and road.
        p.shader = LinearGradient(0f, horizon, 0f, r.bottom, Color.rgb(5, 11, 23), Color.rgb(2, 5, 12), Shader.TileMode.CLAMP)
        c.drawRect(r.left, horizon, r.right, r.bottom, p)
        val surface = Path().apply {
            moveTo(vx - w * 0.02f, horizon); lineTo(vx + w * 0.02f, horizon)
            lineTo(vx + w * 0.32f, r.bottom); lineTo(vx - w * 0.32f, r.bottom); close()
        }
        p.shader = LinearGradient(0f, horizon, 0f, r.bottom, Color.rgb(14, 26, 48), Color.rgb(22, 35, 60), Shader.TileMode.CLAMP)
        c.drawPath(surface, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = w * 0.004f
        p.color = accent; c.drawLine(vx - w * 0.02f, horizon, vx - w * 0.32f, r.bottom, p)
        p.color = accentDeep; c.drawLine(vx + w * 0.02f, horizon, vx + w * 0.32f, r.bottom, p)
        fun yAt(t: Float) = horizon + (r.bottom - horizon) * t * t
        fun xAt(lane: Float, t: Float) = vx + lane * (w * 0.02f + w * 0.30f * t * t)
        // Two dashed lane lines.
        val dashes = 6
        for (lane in floatArrayOf(-0.33f, 0.33f)) {
            for (i in 0 until dashes) {
                val t0 = (i + f.roadPhase) / dashes
                val t1 = (t0 + 0.06f).coerceAtMost(1f)
                p.color = Color.argb((30 + 150 * t0).toInt(), 255, 255, 255)
                p.strokeWidth = w * (0.0015f + 0.007f * t0)
                c.drawLine(xAt(lane, t0), yAt(t0), xAt(lane, t1), yAt(t1), p)
            }
        }
        // Road lights passing on both sides (one every 25 m).
        val polePhase = (f.travelled / 25f) % 1f
        for (i in 0 until 4) {
            val t = (i + polePhase) / 4
            val y = yAt(t)
            val hgt = (r.bottom - horizon) * 0.12f + h * 0.55f * t * t
            for (side in floatArrayOf(-1f, 1f)) {
                val x = xAt(side * 1.08f, t)
                p.style = Paint.Style.STROKE; p.strokeWidth = w * (0.001f + 0.004f * t)
                p.color = Color.argb((80 + 150 * t).toInt(), 38, 53, 79)
                c.drawLine(x, y, x, y - hgt, p)
                val lx = x - side * hgt * 0.12f
                val ly = y - hgt
                val gr = w * (0.008f + 0.03f * t)
                p.style = Paint.Style.FILL
                p.shader = RadialGradient(lx, ly, gr, Color.argb((140 * t).toInt(), 64, 208, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawCircle(lx, ly, gr, p); p.shader = null
                p.color = Color.argb((100 + 155 * t).toInt(), 255, 255, 255)
                c.drawCircle(lx, ly, w * (0.0015f + 0.004f * t), p)
            }
        }
        // Speed streaks above 40 km/h.
        val streak = (((f.speed ?: 0f) - 40f) / 100f).coerceIn(0f, 1f)
        if (streak > 0f) {
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.003f
            for (k in 0 until 6) {
                val t = ((k / 6f) + (f.travelled / 6f) % 1f) % 1f
                val side = if (k % 2 == 0) -1f else 1f
                val t1 = (t + 0.18f).coerceAtMost(1f)
                p.color = Color.argb((90 * streak * t).toInt(), 64, 208, 255)
                c.drawLine(xAt(side * 0.75f, t), yAt(t), xAt(side * 0.75f, t1), yAt(t1), p)
            }
        }
        p.style = Paint.Style.FILL
        val moving = (f.speed ?: 0f) > 1f
        val bob = if (moving) sin(f.travelled * 2.1f) * h * 0.004f * ((f.speed ?: 0f) / 120f).coerceAtMost(1f) else 0f
        car(c, vx, r.bottom - h * 0.20f + bob, min(w * 0.30f, h * 0.62f), f.braking || !moving)
        c.restore()
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.003f
        p.color = Color.argb(110, 64, 208, 255)
        c.drawRoundRect(r, radius, radius, p)
    }

    /** JAC J7-style liftback seen from behind (no manufacturer logo). Mirrors the phone drawing. */
    private fun car(c: Canvas, cx: Float, cy: Float, cw: Float, brakes: Boolean) {
        val ch = cw * 0.60f
        val l = cx - cw / 2
        val t = cy - ch / 2
        fun x(f: Float) = l + cw * f
        fun y(f: Float) = t + ch * f
        val tail = Color.rgb(255, 34, 54)
        val lit = if (brakes) 255 else 200
        p.reset(); p.isAntiAlias = true
        // Ground glow, red light on the road, shadow.
        p.shader = RadialGradient(cx, y(1f), cw * 0.75f, Color.argb(100, 64, 208, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawOval(RectF(x(-0.25f), y(0.82f), x(1.25f), y(1.22f)), p)
        p.shader = RadialGradient(cx, y(1.02f), cw * 0.6f, Color.argb(if (brakes) 115 else 56, 255, 34, 54), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawOval(RectF(x(-0.1f), y(0.9f), x(1.1f), y(1.2f)), p); p.shader = null
        p.color = Color.argb(180, 0, 0, 0); c.drawOval(RectF(x(-0.02f), y(0.90f), x(1.02f), y(1.04f)), p)
        // Tyres.
        p.color = Color.rgb(7, 9, 14)
        c.drawRoundRect(RectF(x(0.04f), y(0.76f), x(0.19f), y(0.98f)), cw * 0.03f, cw * 0.03f, p)
        c.drawRoundRect(RectF(x(0.81f), y(0.76f), x(0.96f), y(0.98f)), cw * 0.03f, cw * 0.03f, p)
        // Body.
        val body = Path().apply {
            moveTo(x(0.30f), y(0.04f))
            quadTo(x(0.5f), y(0.0f), x(0.70f), y(0.04f))
            quadTo(x(0.86f), y(0.20f), x(0.95f), y(0.38f))
            quadTo(x(1.01f), y(0.46f), x(0.995f), y(0.62f))
            lineTo(x(0.975f), y(0.86f))
            quadTo(x(0.965f), y(0.92f), x(0.89f), y(0.92f))
            lineTo(x(0.11f), y(0.92f))
            quadTo(x(0.035f), y(0.92f), x(0.025f), y(0.86f))
            lineTo(x(0.005f), y(0.62f))
            quadTo(x(-0.01f), y(0.46f), x(0.05f), y(0.38f))
            quadTo(x(0.14f), y(0.20f), x(0.30f), y(0.04f))
            close()
        }
        p.shader = LinearGradient(0f, y(0f), 0f, y(0.92f), intArrayOf(Color.rgb(140, 151, 168), Color.rgb(74, 83, 99), Color.rgb(38, 44, 55)), null, Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = cw * 0.006f
        p.shader = LinearGradient(0f, y(0f), 0f, y(0.7f), Color.argb(140, 64, 208, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawPath(body, p); p.shader = null; p.style = Paint.Style.FILL
        // Rear screen.
        val glass = Path().apply {
            moveTo(x(0.33f), y(0.075f)); quadTo(x(0.5f), y(0.045f), x(0.67f), y(0.075f))
            lineTo(x(0.84f), y(0.33f)); quadTo(x(0.5f), y(0.36f), x(0.16f), y(0.33f)); close()
        }
        p.shader = LinearGradient(0f, y(0.05f), 0f, y(0.35f), Color.rgb(42, 59, 87), Color.rgb(11, 17, 28), Shader.TileMode.CLAMP)
        c.drawPath(glass, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.argb(40, 255, 255, 255); p.strokeWidth = cw * 0.012f
        c.drawLine(x(0.60f), y(0.09f), x(0.70f), y(0.31f), p)
        // Roof spoiler, silver C-pillar trim, ducktail lip.
        p.color = Color.rgb(13, 18, 27); p.strokeWidth = ch * 0.03f
        c.drawLine(x(0.34f), y(0.07f), x(0.66f), y(0.07f), p)
        p.color = Color.rgb(228, 233, 240); p.strokeWidth = ch * 0.016f
        for (side in floatArrayOf(-1f, 1f)) {
            val sx = { f: Float -> cx + side * cw * f }
            c.drawPath(Path().apply { moveTo(sx(0.21f), y(0.045f)); quadTo(sx(0.36f), y(0.16f), sx(0.44f), y(0.36f)) }, p)
        }
        p.color = Color.rgb(13, 18, 27); p.strokeWidth = ch * 0.025f
        c.drawPath(Path().apply { moveTo(x(0.08f), y(0.385f)); quadTo(x(0.5f), y(0.355f), x(0.92f), y(0.385f)) }, p)
        p.color = Color.argb(180, 228, 233, 240); p.strokeWidth = ch * 0.008f
        c.drawPath(Path().apply { moveTo(x(0.12f), y(0.37f)); quadTo(x(0.5f), y(0.343f), x(0.88f), y(0.37f)) }, p)
        // Full-width light bar.
        val barY = y(0.47f)
        p.color = Color.argb(if (brakes) 128 else 64, 255, 34, 54); p.strokeWidth = ch * 0.12f
        c.drawLine(x(0.06f), barY, x(0.94f), barY, p)
        p.color = Color.rgb(42, 7, 16); p.strokeWidth = ch * 0.03f
        c.drawLine(x(0.10f), barY, x(0.90f), barY, p)
        p.color = Color.argb(lit, 255, 34, 54); p.strokeWidth = ch * 0.012f
        c.drawLine(x(0.12f), barY, x(0.88f), barY, p)
        p.style = Paint.Style.FILL
        for (side in floatArrayOf(-1f, 1f)) {
            val sx = { f: Float -> cx + side * cw * f }
            p.color = Color.rgb(58, 6, 16)
            c.drawPath(Path().apply {
                moveTo(sx(0.50f), y(0.41f)); lineTo(sx(0.47f), y(0.535f)); lineTo(sx(0.30f), y(0.505f))
                lineTo(sx(0.24f), y(0.485f)); lineTo(sx(0.24f), y(0.455f)); lineTo(sx(0.31f), y(0.435f)); close()
            }, p)
            p.color = Color.argb(lit, 255, 34, 54)
            c.drawPath(Path().apply { moveTo(sx(0.49f), y(0.425f)); lineTo(sx(0.465f), y(0.52f)); lineTo(sx(0.39f), y(0.505f)); lineTo(sx(0.40f), y(0.44f)); close() }, p)
            val clear = Path().apply { moveTo(sx(0.38f), y(0.445f)); lineTo(sx(0.375f), y(0.505f)); lineTo(sx(0.29f), y(0.49f)); lineTo(sx(0.29f), y(0.455f)); close() }
            p.color = Color.argb(216, 217, 221, 228); c.drawPath(clear, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = ch * 0.008f; p.color = Color.argb(lit, 255, 34, 54); c.drawPath(clear, p)
            p.strokeWidth = ch * 0.010f; p.color = Color.argb(lit, 255, 196, 202)
            c.drawLine(sx(0.48f), y(0.44f), sx(0.41f), y(0.45f), p)
            p.style = Paint.Style.FILL; p.color = Color.argb(190, 255, 34, 54)
            c.drawPath(Path().apply { moveTo(sx(0.46f), y(0.66f)); lineTo(sx(0.45f), y(0.72f)); lineTo(sx(0.34f), y(0.735f)); lineTo(sx(0.34f), y(0.69f)); close() }, p)
        }
        // Plain badge, plate, diffuser, exhaust.
        p.style = Paint.Style.STROKE; p.strokeWidth = ch * 0.007f; p.color = Color.rgb(205, 212, 222)
        c.drawOval(RectF(cx - cw * 0.045f, barY - ch * 0.022f, cx + cw * 0.045f, barY + ch * 0.022f), p)
        p.style = Paint.Style.FILL
        p.color = Color.rgb(26, 32, 43); c.drawRoundRect(RectF(cx - cw * 0.14f, y(0.58f), cx + cw * 0.14f, y(0.71f)), ch * 0.02f, ch * 0.02f, p)
        p.color = Color.rgb(233, 238, 245); c.drawRoundRect(RectF(cx - cw * 0.12f, y(0.595f), cx + cw * 0.12f, y(0.69f)), ch * 0.01f, ch * 0.01f, p)
        p.color = Color.rgb(12, 16, 24); c.drawRoundRect(RectF(x(0.10f), y(0.80f), x(0.90f), y(0.90f)), ch * 0.04f, ch * 0.04f, p)
        p.color = Color.rgb(154, 164, 180); c.drawOval(RectF(x(0.24f), y(0.83f), x(0.31f), y(0.88f)), p)
        p.color = Color.rgb(5, 7, 11); c.drawOval(RectF(x(0.25f), y(0.84f), x(0.30f), y(0.87f)), p)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface?, align: Paint.Align) {
        p.reset(); p.isAntiAlias = true
        p.color = color; p.textSize = size; p.typeface = tf; p.textAlign = align
        c.drawText(s, x, y, p)
    }
}
