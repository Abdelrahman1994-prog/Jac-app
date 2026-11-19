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

    /** Perspective road; lane marks move with [CockpitFrame.roadPhase]. Illustrative only. */
    private fun road(c: Canvas, r: RectF, f: CockpitFrame) {
        val w = r.width()
        val h = r.height()
        val vx = r.centerX()
        val vy = r.top + h * 0.04f
        val half = w * 0.42f
        // Road surface.
        val surface = Path().apply {
            moveTo(vx - w * 0.015f, vy); lineTo(vx + w * 0.015f, vy)
            lineTo(vx + half, r.bottom); lineTo(vx - half, r.bottom); close()
        }
        // Skyline / equaliser bars on the horizon (static decoration, like the cluster).
        p.reset(); p.isAntiAlias = true
        val bars = 34
        for (i in 0 until bars) {
            val bx = r.left + w * 0.1f + (w * 0.8f) * i / (bars - 1)
            val bh = h * (0.04f + 0.12f * (((i * 37 + 11) % 17) / 16f)) * (1f - kotlin.math.abs(i - bars / 2f) / bars)
            p.color = Color.argb(70, 64, 208, 255)
            c.drawRect(bx - w * 0.004f, vy - bh, bx + w * 0.004f, vy, p)
        }
        p.shader = LinearGradient(0f, vy, 0f, r.bottom, Color.argb(30, 28, 108, 255), Color.argb(160, 28, 108, 255), Shader.TileMode.CLAMP)
        c.drawPath(surface, p); p.shader = null
        // Edges.
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = w * 0.008f
        p.shader = LinearGradient(0f, vy, 0f, r.bottom, Color.argb(0, 64, 208, 255), Color.argb(255, 120, 225, 255), Shader.TileMode.CLAMP)
        c.drawLine(vx - w * 0.015f, vy, vx - half, r.bottom, p)
        c.drawLine(vx + w * 0.015f, vy, vx + half, r.bottom, p)
        p.shader = null
        // Moving centre dashes.
        val n = 7
        for (i in 0 until n) {
            val t0 = ((i + f.roadPhase) / n)
            val t1 = (t0 + 0.045f).coerceAtMost(1f)
            val y0 = vy + (r.bottom - vy) * t0.pow(2.2f)
            val y1 = vy + (r.bottom - vy) * t1.pow(2.2f)
            p.color = Color.argb((50 + 190 * t0).toInt().coerceAtMost(255), 210, 240, 255)
            p.strokeWidth = w * (0.003f + 0.012f * t0)
            c.drawLine(vx, y0, vx, y1, p)
        }
        car(c, vx, r.bottom - h * 0.22f, min(w * 0.24f, h * 0.55f))
    }

    private fun car(c: Canvas, cx: Float, cy: Float, cw: Float) {
        val ch = cw * 0.78f
        val l = cx - cw / 2
        val t = cy - ch / 2
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, t + ch, cw * 0.8f, Color.argb(110, 64, 208, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawOval(RectF(l - cw * 0.25f, t + ch * 0.8f, l + cw * 1.25f, t + ch * 1.25f), p); p.shader = null
        p.color = Color.argb(150, 0, 0, 0); c.drawOval(RectF(l - cw * 0.04f, t + ch * 0.88f, l + cw * 1.04f, t + ch * 1.08f), p)
        p.color = Color.rgb(11, 14, 18)
        c.drawRoundRect(RectF(l + cw * 0.04f, t + ch * 0.78f, l + cw * 0.22f, t + ch), cw * 0.03f, cw * 0.03f, p)
        c.drawRoundRect(RectF(l + cw * 0.78f, t + ch * 0.78f, l + cw * 0.96f, t + ch), cw * 0.03f, cw * 0.03f, p)
        val body = Path().apply {
            moveTo(l + cw * 0.04f, t + ch * 0.42f); lineTo(l + cw * 0.96f, t + ch * 0.42f)
            quadTo(l + cw, t + ch * 0.46f, l + cw, t + ch * 0.56f); lineTo(l + cw * 0.98f, t + ch * 0.84f)
            quadTo(l + cw * 0.97f, t + ch * 0.9f, l + cw * 0.9f, t + ch * 0.9f); lineTo(l + cw * 0.1f, t + ch * 0.9f)
            quadTo(l + cw * 0.03f, t + ch * 0.9f, l + cw * 0.02f, t + ch * 0.84f); lineTo(l, t + ch * 0.56f)
            quadTo(l, t + ch * 0.46f, l + cw * 0.04f, t + ch * 0.42f); close()
        }
        p.shader = LinearGradient(0f, t + ch * 0.42f, 0f, t + ch * 0.9f, Color.rgb(250, 252, 255), Color.rgb(176, 188, 204), Shader.TileMode.CLAMP)
        c.drawPath(body, p)
        val cabin = Path().apply {
            moveTo(l + cw * 0.14f, t + ch * 0.44f); lineTo(l + cw * 0.24f, t + ch * 0.08f)
            quadTo(l + cw * 0.26f, t + ch * 0.02f, l + cw * 0.32f, t + ch * 0.02f); lineTo(l + cw * 0.68f, t + ch * 0.02f)
            quadTo(l + cw * 0.74f, t + ch * 0.02f, l + cw * 0.76f, t + ch * 0.08f); lineTo(l + cw * 0.86f, t + ch * 0.44f); close()
        }
        p.shader = LinearGradient(0f, t, 0f, t + ch * 0.44f, Color.rgb(232, 238, 246), Color.rgb(250, 252, 255), Shader.TileMode.CLAMP)
        c.drawPath(cabin, p); p.shader = null
        val glass = Path().apply {
            moveTo(l + cw * 0.22f, t + ch * 0.36f); lineTo(l + cw * 0.29f, t + ch * 0.10f)
            lineTo(l + cw * 0.71f, t + ch * 0.10f); lineTo(l + cw * 0.78f, t + ch * 0.36f); close()
        }
        p.color = Color.rgb(20, 30, 44); c.drawPath(glass, p)
        // Tail lamps.
        p.color = red
        c.drawRoundRect(RectF(l + cw * 0.05f, t + ch * 0.49f, l + cw * 0.25f, t + ch * 0.535f), ch * 0.02f, ch * 0.02f, p)
        c.drawRoundRect(RectF(l + cw * 0.75f, t + ch * 0.49f, l + cw * 0.95f, t + ch * 0.535f), ch * 0.02f, ch * 0.02f, p)
        p.strokeWidth = ch * 0.012f; c.drawLine(l + cw * 0.25f, t + ch * 0.51f, l + cw * 0.75f, t + ch * 0.51f, p)
        p.color = Color.rgb(150, 160, 175)
        c.drawRoundRect(RectF(cx - cw * 0.14f, t + ch * 0.62f, cx + cw * 0.14f, t + ch * 0.72f), ch * 0.02f, ch * 0.02f, p)
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
