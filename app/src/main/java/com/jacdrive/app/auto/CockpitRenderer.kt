package com.jacdrive.app.auto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
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
) {
    data class Labels(val rpm: String, val coolant: String, val battery: String, val fuel: String, val load: String, val demo: String, val mil: String)
}

/**
 * Draws the full cockpit with android.graphics so it can be rendered on the Android Auto map
 * surface. Large, high-contrast numerals; no blinking. Pure drawing — testable on a Bitmap.
 */
class CockpitRenderer(private val bold: Typeface?, private val light: Typeface?) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = Color.rgb(43, 181, 242)
    private val amber = Color.rgb(255, 162, 62)
    private val red = Color.rgb(255, 77, 94)
    private val dim = Color.rgb(133, 146, 163)

    /** @param area the part of the surface not covered by the host's controls. */
    fun draw(c: Canvas, area: Rect, f: CockpitFrame) {
        // Background.
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, 0f, c.height.toFloat(), Color.rgb(27, 34, 44), Color.rgb(8, 11, 15), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), p)
        p.shader = null

        val w = area.width().toFloat()
        val h = area.height().toFloat()
        if (h > w * 1.05f) drawPortrait(c, area, f) else drawLandscape(c, area, f, w, h)
    }

    private fun tiles(f: CockpitFrame) = listOf(
        Triple(f.labels.coolant, f.coolant?.let { "%.0f %s".format(it, f.coolantUnit) }, if (f.coolantHot) red else null),
        Triple(f.labels.battery, f.battery?.let { "%.1f V".format(it) }, if (f.batteryBad) amber else null),
        Triple(f.labels.fuel, f.fuel?.let { "%.0f %%".format(it) }, if ((f.fuel ?: 100f) < 10f) amber else null),
        Triple(f.labels.load, f.load?.let { "%.0f %%".format(it) }, null),
    )

    private fun speedDial(c: Canvas, cx: Float, cy: Float, d: Float, f: CockpitFrame) =
        dial(c, cx, cy, d, f.speed?.div(f.speedMax), if (f.demo) Color.rgb(181, 123, 255) else accent, null,
            f.speed?.let { "%.0f".format(it) } ?: "—", f.speedUnit)

    private fun rpmDial(c: Canvas, cx: Float, cy: Float, d: Float, f: CockpitFrame) {
        val rpmFrac = f.rpm?.div(8000f)
        val redFrac = f.redline / 8000f
        dial(c, cx, cy, d, rpmFrac, if (rpmFrac != null && rpmFrac >= redFrac) red else amber, redFrac,
            f.rpm?.let { "%.1f".format(it / 1000f) } ?: "—", f.labels.rpm)
    }

    /** Tall screens (e.g. the JAC J7's portrait head unit): dials on top, a 2×2 tile grid below. */
    private fun drawPortrait(c: Canvas, area: Rect, f: CockpitFrame) {
        val w = area.width().toFloat()
        val h = area.height().toFloat()
        var y = area.top + h * 0.045f
        text(c, f.status, area.exactCenterX(), y, min(w * 0.045f, h * 0.032f), if (f.demo) Color.rgb(181, 123, 255) else dim, bold, Paint.Align.CENTER)
        if (f.milOn) {
            y += h * 0.04f
            text(c, f.labels.mil, area.exactCenterX(), y, min(w * 0.05f, h * 0.035f), red, bold, Paint.Align.CENTER)
        }
        val dialTop = area.top + h * 0.09f
        val d = min(w * 0.47f, h * 0.38f)
        val cy = dialTop + d / 2
        speedDial(c, area.left + w * 0.25f, cy, d, f)
        rpmDial(c, area.left + w * 0.75f, cy, d, f)

        val gridTop = dialTop + d + h * 0.03f
        val gridBottom = area.bottom - h * (if (f.demo) 0.06f else 0.03f)
        val gap = w * 0.03f
        val cellW = (w - gap * 3) / 2
        val cellH = ((gridBottom - gridTop) - gap) / 2
        tiles(f).forEachIndexed { i, t ->
            val col = i % 2
            val row = i / 2
            val left = area.left + gap + col * (cellW + gap)
            val top = gridTop + row * (cellH + gap)
            tile(c, RectF(left, top, left + cellW, top + cellH), t)
        }
        if (f.demo) text(c, f.labels.demo, area.exactCenterX(), area.bottom - h * 0.015f, min(w * 0.04f, h * 0.03f), Color.rgb(181, 123, 255), bold, Paint.Align.CENTER)
    }

    private fun drawLandscape(c: Canvas, area: Rect, f: CockpitFrame, w: Float, h: Float) {
        val dial = min(w * 0.30f, h * 0.84f)
        val cy = area.top + h * 0.52f
        val leftCx = area.left + w * 0.04f + dial / 2
        val rightCx = area.right - w * 0.04f - dial / 2
        speedDial(c, leftCx, cy, dial, f)
        rpmDial(c, rightCx, cy, dial, f)

        val colL = leftCx + dial / 2 + w * 0.02f
        val colR = rightCx - dial / 2 - w * 0.02f
        var y = area.top + h * 0.10f
        text(c, f.status, (colL + colR) / 2, y, h * 0.045f, if (f.demo) Color.rgb(181, 123, 255) else dim, bold, Paint.Align.CENTER)
        if (f.milOn) {
            y += h * 0.075f
            text(c, f.labels.mil, (colL + colR) / 2, y, h * 0.05f, red, bold, Paint.Align.CENTER)
        }
        val list = tiles(f)
        val tileTop = area.top + h * 0.24f
        val tileH = (area.bottom - h * 0.06f - tileTop) / list.size
        list.forEachIndexed { i, t ->
            val top = tileTop + i * tileH
            tile(c, RectF(colL, top + tileH * 0.08f, colR, top + tileH * 0.92f), t)
        }
        if (f.demo) text(c, f.labels.demo, (colL + colR) / 2, area.bottom - h * 0.015f, h * 0.035f, Color.rgb(181, 123, 255), bold, Paint.Align.CENTER)
    }

    /** Stacked tile: small label on top, large value underneath — never overlaps, any size. */
    private fun tile(c: Canvas, r: RectF, t: Triple<String, String?, Int?>) {
        val (label, value, warn) = t
        val radius = min(r.height(), r.width()) * 0.16f
        p.reset(); p.isAntiAlias = true
        p.color = Color.argb(20, 255, 255, 255); c.drawRoundRect(r, radius, radius, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r.height() * 0.025f
        p.color = warn ?: Color.argb(40, 255, 255, 255); c.drawRoundRect(r, radius, radius, p)
        val pad = r.width() * 0.07f
        val labelSize = min(r.height() * 0.2f, r.width() * 0.085f)
        val valueSize = min(r.height() * 0.42f, r.width() * 0.2f)
        text(c, label.uppercase(), r.left + pad, r.top + r.height() * 0.1f + labelSize, labelSize, dim, bold, Paint.Align.LEFT)
        text(c, value ?: "—", r.left + pad, r.bottom - r.height() * 0.14f, valueSize, warn ?: Color.WHITE, bold, Paint.Align.LEFT)
    }

    private fun dial(c: Canvas, cx: Float, cy: Float, d: Float, fraction: Float?, color: Int, danger: Float?, value: String, unit: String) {
        val stroke = d * 0.045f
        val r = d / 2 - stroke
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val start = 125f
        val sweep = 290f
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = stroke
        p.color = Color.rgb(42, 51, 63); c.drawArc(rect, start, sweep, false, p)
        if (danger != null) {
            val inner = RectF(rect).apply { inset(stroke * 1.2f, stroke * 1.2f) }
            p.strokeWidth = stroke * 0.35f; p.strokeCap = Paint.Cap.BUTT; p.color = red
            c.drawArc(inner, start + sweep * danger, sweep * (1 - danger), false, p)
            p.strokeWidth = stroke; p.strokeCap = Paint.Cap.ROUND
        }
        val fr = fraction?.coerceIn(0f, 1f)
        if (fr != null && fr > 0.004f) {
            p.color = Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)); p.strokeWidth = stroke * 2.2f
            c.drawArc(rect, start, sweep * fr, false, p)
            p.color = color; p.strokeWidth = stroke
            c.drawArc(rect, start, sweep * fr, false, p)
            // Floating pointer on the ring.
            val a = Math.toRadians((start + sweep * fr).toDouble())
            p.color = Color.WHITE; p.strokeWidth = stroke * 0.7f
            c.drawLine(cx + cos(a).toFloat() * (r * 0.72f), cy + sin(a).toFloat() * (r * 0.72f), cx + cos(a).toFloat() * (r - stroke), cy + sin(a).toFloat() * (r - stroke), p)
        }
        text(c, value, cx, cy + d * 0.08f, d * 0.26f, Color.WHITE, light ?: bold, Paint.Align.CENTER)
        text(c, unit, cx, cy + d * 0.20f, d * 0.065f, dim, bold, Paint.Align.CENTER)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface?, align: Paint.Align) {
        p.reset(); p.isAntiAlias = true
        p.color = color; p.textSize = size; p.typeface = tf; p.textAlign = align
        c.drawText(s, x, y, p)
    }
}
