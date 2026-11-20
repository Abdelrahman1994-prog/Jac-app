package com.jacdrive.app.auto

import android.graphics.BlurMaskFilter
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
    /** Check-engine banner: [ENGINE_FAULT], [ENGINE_OK] (recent clean scan) or [ENGINE_UNKNOWN]. */
    val engineState: Int = ENGINE_UNKNOWN,
    /** Text after "CHECK ENGINE" in the banner, e.g. "FAULT DETECTED". */
    val engineText: String = "",
    /** Coolant within 5° of the user's alert threshold (orange). [coolantHot] = at/above it (red). */
    val coolantWarm: Boolean = false,
    /** Connected to a live adapter (green dot). */
    val connected: Boolean = false,
    val brandMake: String = "JAC",
    val brandModel: String = "J7",
) {
    data class Labels(
        val rpm: String, val coolant: String, val battery: String, val fuel: String, val load: String,
        val demo: String, val mil: String, val throttle: String = "Throttle", val intake: String = "Intake",
        val trip: String = "Trip", val avgFuel: String = "Avg",
    )

    companion object {
        const val ENGINE_UNKNOWN = 0
        const val ENGINE_OK = 1
        const val ENGINE_FAULT = 2
    }
}

/**
 * Draws the cockpit with android.graphics for the Android Auto map surface, laid out like a
 * premium digital cluster: header bar, check-engine banner, two large dials, the car on a road
 * that moves with the real speed, six glass metric cards and a trip bar.
 * Large numerals, no blinking. Pure drawing — testable on a Bitmap.
 */
class CockpitRenderer(private val bold: Typeface?, private val light: Typeface?) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = Color.rgb(56, 200, 255)
    private val blue = Color.rgb(28, 108, 255)
    private val textCyan = Color.rgb(110, 205, 255)
    private val track = Color.rgb(18, 38, 70)
    private val orange = Color.rgb(255, 140, 26)
    private val red = Color.rgb(255, 48, 56)
    private val green = Color.rgb(51, 226, 138)
    private val amber = Color.rgb(255, 176, 32)
    private val dim = Color.rgb(140, 170, 205)
    private val purple = Color.rgb(181, 123, 255)

    private enum class Icon { COOLANT, BATTERY, FUEL, ENGINE, THROTTLE, INTAKE }
    private data class Card(val icon: Icon, val label: String, val value: String?, val unit: String, val fraction: Float?, val level: Int)

    fun draw(c: Canvas, area: Rect, f: CockpitFrame) {
        background(c)
        val w = area.width().toFloat()
        val h = area.height().toFloat()
        if (h > w * 1.05f) drawPortrait(c, area, f, w, h) else drawLandscape(c, area, f, w, h)
    }

    private fun accentOf(f: CockpitFrame) = if (f.demo) purple else cyan

    private fun background(c: Canvas) {
        val cw = c.width.toFloat()
        val chh = c.height.toFloat()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, 0f, chh, intArrayOf(Color.rgb(8, 20, 44), Color.rgb(3, 8, 20), Color.rgb(0, 2, 6)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, cw, chh, p)
        p.shader = RadialGradient(cw / 2, chh * 0.42f, max(cw, chh) * 0.5f, Color.argb(60, 28, 108, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, cw, chh, p)
        p.shader = null
    }

    // ------------------------------------------------------------------ layouts

    /** Tall screens (the JAC J7's portrait head unit): the reference layout. */
    private fun drawPortrait(c: Canvas, area: Rect, f: CockpitFrame, w: Float, h: Float) {
        val pad = w * 0.025f
        val l = area.left + pad
        val r = area.right - pad
        var y = area.top + h * 0.012f
        header(c, RectF(l, y, r, y + h * 0.058f), f); y += h * 0.058f + h * 0.016f
        banner(c, RectF(area.left + w * 0.15f, y, area.right - w * 0.15f, y + h * 0.054f), f); y += h * 0.054f + h * 0.012f

        val d = min(w * 0.46f, h * 0.30f)
        val dialCy = y + d / 2
        val stageTop = dialCy + d * 0.22f
        val stageBottom = y + d + h * 0.10f
        stage(c, RectF(area.left.toFloat(), stageTop, area.right.toFloat(), stageBottom), f, carWidth = w * 0.25f)
        speedDial(c, area.left + w * 0.255f, dialCy, d, f)
        rpmDial(c, area.left + w * 0.745f, dialCy, d, f)

        val tripH = h * 0.072f
        val tripTop = area.bottom - h * 0.012f - tripH
        val gridTop = stageBottom + h * 0.012f
        cardGrid(c, RectF(l, gridTop, r, tripTop - h * 0.018f), f, w * 0.022f)
        tripBar(c, RectF(l, tripTop, r, tripTop + tripH), f)
    }

    private fun drawLandscape(c: Canvas, area: Rect, f: CockpitFrame, w: Float, h: Float) {
        val pad = w * 0.015f
        val headerH = h * 0.085f
        header(c, RectF(area.left + w * 0.2f, area.top + h * 0.015f, area.right - w * 0.2f, area.top + h * 0.015f + headerH), f)
        val d = min(w * 0.27f, h * 0.70f)
        val cy = area.top + h * 0.55f
        val leftCx = area.left + pad + d / 2
        val rightCx = area.right - pad - d / 2
        val colL = leftCx + d / 2 + w * 0.012f
        val colR = rightCx - d / 2 - w * 0.012f
        var y = area.top + h * 0.015f + headerH + h * 0.02f
        banner(c, RectF(colL, y, colR, y + h * 0.075f), f); y += h * 0.075f + h * 0.01f
        val stageH = h * 0.25f
        stage(c, RectF(colL, y, colR, y + stageH), f, carWidth = (colR - colL) * 0.30f)
        y += stageH + h * 0.015f
        val tripH = h * 0.10f
        val tripTop = area.bottom - h * 0.02f - tripH
        cardGrid(c, RectF(colL, y, colR, tripTop - h * 0.02f), f, w * 0.008f)
        tripBar(c, RectF(colL, tripTop, colR, tripTop + tripH), f)
        speedDial(c, leftCx, cy, d, f)
        rpmDial(c, rightCx, cy, d, f)
    }

    // ------------------------------------------------------------------ header & banner

    private fun glass(c: Canvas, r: RectF, radius: Float, edge: Int, fillTint: Int? = null, glow: Float = 0f) {
        if (glow > 0f) {
            glowPaint.reset(); glowPaint.isAntiAlias = true; glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = glow; glowPaint.color = Color.argb(110, Color.red(edge), Color.green(edge), Color.blue(edge))
            glowPaint.maskFilter = BlurMaskFilter(glow * 1.5f, BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(r, radius, radius, glowPaint)
        }
        p.reset(); p.isAntiAlias = true
        val tint = fillTint ?: Color.rgb(16, 42, 84)
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom,
            Color.argb(if (fillTint != null) 120 else 95, Color.red(tint), Color.green(tint), Color.blue(tint)),
            Color.argb(200, 4, 10, 24), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, Color.argb(230, Color.red(edge), Color.green(edge), Color.blue(edge)),
            Color.argb(120, Color.red(edge), Color.green(edge), Color.blue(edge)), Shader.TileMode.CLAMP)
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, r.height() * 0.022f)
        c.drawRoundRect(r, radius, radius, p)
        p.shader = null; p.style = Paint.Style.FILL
    }

    private fun header(c: Canvas, r: RectF, f: CockpitFrame) {
        glass(c, r, r.height() / 2, Color.argb(160, 56, 200, 255))
        val ts = r.height() * 0.34f
        val cy = r.centerY()
        val dotColor = when { f.demo -> purple; f.connected -> green; else -> red }
        p.color = dotColor
        p.maskFilter = BlurMaskFilter(ts * 0.5f, BlurMaskFilter.Blur.NORMAL)
        c.drawCircle(r.left + r.height() * 0.55f, cy, ts * 0.42f, p)
        p.maskFilter = null
        c.drawCircle(r.left + r.height() * 0.55f, cy, ts * 0.3f, p)
        val leftEnd = r.left + r.width() * 0.34f
        fitText(c, f.status.uppercase(), r.left + r.height() * 0.95f, cy + ts * 0.36f, ts, leftEnd - r.left - r.height() * 1.05f, Color.WHITE, bold, Paint.Align.LEFT)
        // Dividers.
        p.color = Color.argb(140, 56, 200, 255); p.strokeWidth = max(1.5f, r.height() * 0.02f)
        val rightStart = r.right - r.width() * 0.34f
        c.drawLine(leftEnd, r.top + r.height() * 0.22f, leftEnd, r.bottom - r.height() * 0.22f, p)
        c.drawLine(rightStart, r.top + r.height() * 0.22f, rightStart, r.bottom - r.height() * 0.22f, p)
        // Brand: make in white, model in red, bold italic.
        val bs = r.height() * 0.55f
        p.reset(); p.isAntiAlias = true; p.typeface = Typeface.create(bold, Typeface.BOLD_ITALIC); p.textSize = bs; p.textSkewX = -0.18f
        val make = f.brandMake.uppercase()
        val model = " " + f.brandModel.uppercase()
        val total = p.measureText(make) + p.measureText(model)
        val bx = r.centerX() - total / 2
        p.color = Color.WHITE; c.drawText(make, bx, cy + bs * 0.36f, p)
        p.color = red; c.drawText(model, bx + p.measureText(make), cy + bs * 0.36f, p)
        p.textSkewX = 0f
        // Right segment: data source.
        val tag = if (f.demo) f.labels.demo.substringBefore(' ').uppercase() else if (f.connected) "LIVE" else "—"
        text(c, tag, (rightStart + r.right) / 2, cy + ts * 0.36f, ts, if (f.demo) purple else if (f.connected) green else dim, bold, Paint.Align.CENTER)
    }

    private fun banner(c: Canvas, r: RectF, f: CockpitFrame) {
        val color = when (f.engineState) {
            CockpitFrame.ENGINE_FAULT -> red
            CockpitFrame.ENGINE_OK -> green
            else -> amber
        }
        val cut = r.height() * 0.45f
        val shape = Path().apply {
            moveTo(r.left + cut, r.top); lineTo(r.right - cut, r.top); lineTo(r.right, r.centerY())
            lineTo(r.right - cut, r.bottom); lineTo(r.left + cut, r.bottom); lineTo(r.left, r.centerY()); close()
        }
        glowPaint.reset(); glowPaint.isAntiAlias = true; glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = r.height() * 0.12f
        glowPaint.color = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
        glowPaint.maskFilter = BlurMaskFilter(r.height() * 0.25f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(shape, glowPaint)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(r.left, 0f, r.right, 0f,
            intArrayOf(Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(150, Color.red(color) / 3, Color.green(color) / 3, Color.blue(color) / 3), Color.argb(60, Color.red(color), Color.green(color), Color.blue(color))),
            null, Shader.TileMode.CLAMP)
        c.drawPath(shape, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, r.height() * 0.035f); p.color = color
        c.drawPath(shape, p); p.style = Paint.Style.FILL
        // Warning triangle.
        val ts = r.height() * 0.40f
        val ix = r.left + r.width() * 0.17f
        val tri = Path().apply { moveTo(ix, r.centerY() - ts * 0.75f); lineTo(ix + ts * 0.8f, r.centerY() + ts * 0.6f); lineTo(ix - ts * 0.8f, r.centerY() + ts * 0.6f); close() }
        p.color = color; c.drawPath(tri, p)
        p.color = Color.rgb(20, 4, 6)
        c.drawRect(ix - ts * 0.07f, r.centerY() - ts * 0.35f, ix + ts * 0.07f, r.centerY() + ts * 0.18f, p)
        c.drawCircle(ix, r.centerY() + ts * 0.36f, ts * 0.08f, p)
        // Text.
        val fs = r.height() * 0.40f
        val split = r.left + r.width() * 0.55f
        fitText(c, f.labels.mil.uppercase(), ix + ts * 1.2f, r.centerY() + fs * 0.36f, fs, split - ix - ts * 1.5f, color, bold, Paint.Align.LEFT)
        p.color = Color.argb(160, Color.red(color), Color.green(color), Color.blue(color)); p.strokeWidth = max(1.5f, r.height() * 0.03f)
        c.drawLine(split, r.top + r.height() * 0.22f, split, r.bottom - r.height() * 0.22f, p)
        fitText(c, f.engineText.uppercase(), split + r.height() * 0.4f, r.centerY() + fs * 0.36f, fs, r.right - cut - split - r.height() * 0.4f, Color.WHITE, bold, Paint.Align.LEFT)
    }

    // ------------------------------------------------------------------ dials

    private fun speedDial(c: Canvas, cx: Float, cy: Float, d: Float, f: CockpitFrame) {
        val step = if (f.speedMax > 200) 30 else 20
        dial(c, cx, cy, d, f.speed?.div(f.speedMax), accentOf(f), null,
            f.speed?.let { "%.0f".format(it) } ?: "—", f.speedUnit,
            (0..f.speedMax.toInt() step step).map { it.toString() })
    }

    private fun rpmDial(c: Canvas, cx: Float, cy: Float, d: Float, f: CockpitFrame) {
        val frac = f.rpm?.div(8000f)
        val redFrac = f.redline / 8000f
        dial(c, cx, cy, d, frac, if (frac != null && frac >= redFrac) red else accentOf(f), redFrac,
            f.rpm?.let { "%.1f".format(it / 1000f) } ?: "—", f.labels.rpm, (0..8).map { it.toString() })
    }

    private fun dial(c: Canvas, cx: Float, cy: Float, d: Float, fraction: Float?, color: Int, danger: Float?, value: String, unit: String, labels: List<String>) {
        val start = 135f
        val sweep = 270f
        val rOuter = d / 2
        val ringW = d * 0.035f
        val rRing = rOuter - ringW
        val ring = RectF(cx - rRing, cy - rRing, cx + rRing, cy + rRing)
        // Dark face with a soft inner glow.
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, cy, rOuter, intArrayOf(Color.rgb(8, 18, 36), Color.rgb(4, 10, 22), Color.argb(0, 4, 10, 22)), floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rOuter, p); p.shader = null
        // Thin outer bezel ring.
        p.style = Paint.Style.STROKE; p.strokeWidth = d * 0.006f; p.color = Color.argb(120, 56, 200, 255)
        c.drawCircle(cx, cy, rOuter - d * 0.004f, p)
        // Track.
        p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = ringW; p.color = track
        c.drawArc(ring, start, sweep, false, p)
        // Redline band.
        if (danger != null) {
            p.strokeCap = Paint.Cap.BUTT
            p.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(Color.argb(0, 255, 48, 56), red, red), floatArrayOf(0f, 0.05f, 1f))
            val m = android.graphics.Matrix(); m.setRotate(start + sweep * danger - 4f, cx, cy); p.shader.setLocalMatrix(m)
            c.drawArc(ring, start + sweep * danger, sweep * (1 - danger), false, p)
            p.shader = null
        }
        // Value arc with glow.
        val fr = fraction?.coerceIn(0f, 1f)
        if (fr != null && fr > 0.004f) {
            glowPaint.reset(); glowPaint.isAntiAlias = true; glowPaint.style = Paint.Style.STROKE; glowPaint.strokeCap = Paint.Cap.ROUND
            glowPaint.strokeWidth = ringW * 2.2f; glowPaint.color = Color.argb(150, Color.red(color), Color.green(color), Color.blue(color))
            glowPaint.maskFilter = BlurMaskFilter(ringW * 1.6f, BlurMaskFilter.Blur.NORMAL)
            c.drawArc(ring, start, sweep * fr, false, glowPaint)
            p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = ringW
            p.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(blue, color, color), floatArrayOf(0f, 0.4f, 1f))
            val m = android.graphics.Matrix(); m.setRotate(start, cx, cy); p.shader.setLocalMatrix(m)
            c.drawArc(ring, start, sweep * fr, false, p); p.shader = null
        }
        // Ticks and numbers.
        val majors = labels.size - 1
        val rTick = rRing - ringW * 1.3f
        for (i in 0..majors * 5) {
            val a = Math.toRadians((start + sweep * i / (majors * 5f)).toDouble())
            val major = i % 5 == 0
            val len = d * if (major) 0.055f else 0.025f
            val frac = i / (majors * 5f)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = d * if (major) 0.010f else 0.005f
            p.color = if (danger != null && frac >= danger) red else if (major) Color.WHITE else Color.argb(150, 180, 210, 240)
            c.drawLine(cx + cos(a).toFloat() * rTick, cy + sin(a).toFloat() * rTick, cx + cos(a).toFloat() * (rTick - len), cy + sin(a).toFloat() * (rTick - len), p)
            if (major) {
                val rl = rTick - d * 0.11f
                val lc = if (danger != null && frac >= danger) red else Color.WHITE
                text(c, labels[i / 5], cx + cos(a).toFloat() * rl, cy + sin(a).toFloat() * rl + d * 0.022f, d * 0.062f, lc, bold, Paint.Align.CENTER)
            }
        }
        // Needle (short, at the arc tip).
        if (fr != null) {
            val a = Math.toRadians((start + sweep * fr).toDouble())
            p.strokeWidth = d * 0.016f; p.color = Color.WHITE; p.strokeCap = Paint.Cap.ROUND
            c.drawLine(cx + cos(a).toFloat() * (rTick - d * 0.17f), cy + sin(a).toFloat() * (rTick - d * 0.17f), cx + cos(a).toFloat() * (rRing + ringW * 0.3f), cy + sin(a).toFloat() * (rRing + ringW * 0.3f), p)
        }
        p.style = Paint.Style.FILL
        text(c, value, cx, cy + d * 0.08f, d * 0.24f, Color.WHITE, Typeface.create(bold, Typeface.BOLD_ITALIC), Paint.Align.CENTER)
        text(c, unit, cx, cy + d * 0.19f, d * 0.068f, textCyan, bold, Paint.Align.CENTER)
    }

    // ------------------------------------------------------------------ cards & trip

    private fun cards(f: CockpitFrame): List<Card> {
        val celsius = f.coolantUnit == "°C"
        return listOf(
            Card(Icon.COOLANT, f.labels.coolant, f.coolant?.let { "%.0f".format(it) }, f.coolantUnit,
                f.coolant?.let { if (celsius) (it - 40) / 90 else (it - 104) / 162 }, if (f.coolantHot) 2 else if (f.coolantWarm) 1 else 0),
            Card(Icon.BATTERY, f.labels.battery, f.battery?.let { "%.1f".format(it) }, "V", f.battery?.let { (it - 10) / 6 },
                if (f.battery != null && (f.battery < 11.8f || f.battery > 16f)) 2 else if (f.batteryBad) 1 else 0),
            Card(Icon.FUEL, f.labels.fuel, f.fuel?.let { "%.0f".format(it) }, "%", f.fuel?.div(100),
                when { f.fuel == null -> 0; f.fuel < 10f -> 2; f.fuel < 20f -> 1; else -> 0 }),
            Card(Icon.ENGINE, f.labels.load, f.load?.let { "%.0f".format(it) }, "%", f.load?.div(100), 0),
            Card(Icon.THROTTLE, f.labels.throttle, f.throttle?.let { "%.0f".format(it) }, "%", f.throttle?.div(100), 0),
            Card(Icon.INTAKE, f.labels.intake, f.intake?.let { "%.0f".format(it) }, f.coolantUnit,
                f.intake?.let { if (celsius) (it + 10) / 90 else (it - 14) / 162 },
                if (f.intake != null && f.intake > (if (celsius) 60f else 140f)) 1 else 0),
        )
    }

    private fun cardGrid(c: Canvas, r: RectF, f: CockpitFrame, gap: Float) {
        val cw = (r.width() - gap * 2) / 3
        val ch = (r.height() - gap) / 2
        cards(f).forEachIndexed { i, card ->
            val col = i % 3
            val row = i / 3
            val x = r.left + col * (cw + gap)
            val y = r.top + row * (ch + gap)
            card(c, RectF(x, y, x + cw, y + ch), card, f)
        }
    }

    private fun card(c: Canvas, r: RectF, m: Card, f: CockpitFrame) {
        val color = when (m.level) { 2 -> red; 1 -> orange; else -> accentOf(f) }
        glass(c, r, min(r.width(), r.height()) * 0.14f, color, fillTint = if (m.level == 2) Color.rgb(140, 16, 24) else null, glow = if (m.level > 0) r.height() * 0.05f else r.height() * 0.02f)
        val padX = r.width() * 0.07f
        // Icon.
        val iconS = min(r.height() * 0.38f, r.width() * 0.30f)
        icon(c, m.icon, RectF(r.left + padX, r.top + r.height() * 0.16f, r.left + padX + iconS, r.top + r.height() * 0.16f + iconS), color)
        // Value + unit, right aligned.
        val right = r.right - padX
        val vs = min(r.height() * 0.30f, r.width() * 0.22f)
        val valueText = m.value ?: "—"
        val us = vs * 0.55f
        p.reset(); p.isAntiAlias = true; p.typeface = bold; p.textSize = us
        val unitW = if (m.value != null) p.measureText(m.unit) + vs * 0.08f else 0f
        val baseline = r.top + r.height() * 0.44f
        val valueColor = if (m.level == 2) red else Color.WHITE
        text(c, valueText, right - unitW, baseline, vs, valueColor, bold, Paint.Align.RIGHT)
        if (m.value != null) text(c, m.unit, right, baseline, us, valueColor, bold, Paint.Align.RIGHT)
        // Label.
        fitText(c, m.label, right, r.top + r.height() * 0.66f, r.height() * 0.14f, r.width() - padX * 2 - iconS * 0.2f,
            if (m.level == 2) Color.WHITE else Color.rgb(200, 225, 255), bold, Paint.Align.RIGHT)
        // Progress bar.
        val barY = r.bottom - r.height() * 0.15f
        val barH = r.height() * 0.065f
        p.reset(); p.isAntiAlias = true; p.color = Color.argb(70, 120, 160, 210)
        c.drawRoundRect(RectF(r.left + padX, barY, right, barY + barH), barH / 2, barH / 2, p)
        val fr = m.fraction?.coerceIn(0f, 1f)
        if (fr != null && fr > 0.01f) {
            val end = r.left + padX + (right - r.left - padX) * fr
            val bar = RectF(r.left + padX, barY, end, barY + barH)
            glowPaint.reset(); glowPaint.isAntiAlias = true; glowPaint.color = Color.argb(170, Color.red(color), Color.green(color), Color.blue(color))
            glowPaint.maskFilter = BlurMaskFilter(barH * 1.2f, BlurMaskFilter.Blur.NORMAL)
            c.drawRoundRect(bar, barH / 2, barH / 2, glowPaint)
            p.reset(); p.isAntiAlias = true; p.color = color
            c.drawRoundRect(bar, barH / 2, barH / 2, p)
        }
    }

    private fun tripBar(c: Canvas, r: RectF, f: CockpitFrame) {
        glass(c, r, r.height() / 2, Color.argb(200, 56, 200, 255), glow = r.height() * 0.04f)
        val cy = r.centerY()
        val iconS = r.height() * 0.5f
        icon(c, null, RectF(r.left + r.height() * 0.5f, cy - iconS / 2, r.left + r.height() * 0.5f + iconS, cy + iconS / 2), accentOf(f))
        val d1 = r.left + r.height() * 1.4f
        val d2 = r.left + r.width() * 0.56f
        p.color = Color.argb(140, 56, 200, 255); p.strokeWidth = max(1.5f, r.height() * 0.02f)
        c.drawLine(d1, r.top + r.height() * 0.22f, d1, r.bottom - r.height() * 0.22f, p)
        c.drawLine(d2, r.top + r.height() * 0.22f, d2, r.bottom - r.height() * 0.22f, p)
        val fs = r.height() * 0.36f
        pair(c, f.labels.trip.uppercase(), f.trip ?: "—", (d1 + d2) / 2, cy + fs * 0.36f, fs, d2 - d1 - r.height() * 0.3f)
        pair(c, f.labels.avgFuel.uppercase(), f.avgFuel ?: "—", (d2 + r.right - r.height() * 0.3f) / 2, cy + fs * 0.36f, fs, r.right - d2 - r.height() * 0.5f)
    }

    /** "LABEL value" centred, label in cyan, value in white, shrunk to fit [maxW]. */
    private fun pair(c: Canvas, label: String, value: String, cx: Float, baseline: Float, size: Float, maxW: Float) {
        p.reset(); p.isAntiAlias = true; p.typeface = bold
        var s = size
        fun width(): Float { p.textSize = s * 0.85f; val a = p.measureText("$label "); p.textSize = s; return a + p.measureText(value) }
        while (width() > maxW && s > 6f) s *= 0.92f
        val total = width()
        var x = cx - total / 2
        p.textSize = s * 0.85f; p.color = textCyan; p.textAlign = Paint.Align.LEFT
        c.drawText("$label ", x, baseline, p); x += p.measureText("$label ")
        p.textSize = s; p.color = Color.WHITE
        c.drawText(value, x, baseline, p)
    }

    // ------------------------------------------------------------------ icons (line drawings)

    private fun icon(c: Canvas, which: Icon?, b: RectF, color: Int) {
        val s = b.width()
        fun X(f: Float) = b.left + s * f
        fun Y(f: Float) = b.top + s * f
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = color; p.strokeWidth = s * 0.075f
        when (which) {
            Icon.COOLANT -> {
                c.drawRoundRect(RectF(X(0.40f), Y(0.02f), X(0.56f), Y(0.58f)), s * 0.08f, s * 0.08f, p)
                c.drawCircle(X(0.48f), Y(0.66f), s * 0.12f, p)
                for (k in 0..2) c.drawLine(X(0.60f), Y(0.12f + k * 0.12f), X(0.74f), Y(0.12f + k * 0.12f), p)
                val wave = Path(); wave.moveTo(X(0.05f), Y(0.92f))
                for (k in 0 until 4) wave.quadTo(X(0.16f + k * 0.24f), Y(0.82f + (k % 2) * 0.2f), X(0.28f + k * 0.24f), Y(0.92f))
                c.drawPath(wave, p)
            }
            Icon.BATTERY -> {
                c.drawRoundRect(RectF(X(0.04f), Y(0.26f), X(0.96f), Y(0.86f)), s * 0.06f, s * 0.06f, p)
                c.drawLine(X(0.20f), Y(0.17f), X(0.34f), Y(0.17f), p); c.drawLine(X(0.66f), Y(0.17f), X(0.80f), Y(0.17f), p)
                c.drawLine(X(0.18f), Y(0.55f), X(0.38f), Y(0.55f), p); c.drawLine(X(0.28f), Y(0.45f), X(0.28f), Y(0.65f), p)
                c.drawLine(X(0.62f), Y(0.55f), X(0.82f), Y(0.55f), p)
            }
            Icon.FUEL -> {
                c.drawRoundRect(RectF(X(0.10f), Y(0.06f), X(0.60f), Y(0.94f)), s * 0.07f, s * 0.07f, p)
                c.drawRect(X(0.20f), Y(0.18f), X(0.50f), Y(0.40f), p)
                val hose = Path().apply { moveTo(X(0.60f), Y(0.30f)); lineTo(X(0.80f), Y(0.42f)); lineTo(X(0.80f), Y(0.78f)); quadTo(X(0.80f), Y(0.88f), X(0.90f), Y(0.84f)); lineTo(X(0.90f), Y(0.30f)); lineTo(X(0.78f), Y(0.16f)) }
                c.drawPath(hose, p)
            }
            Icon.ENGINE -> {
                val e = Path().apply {
                    moveTo(X(0.30f), Y(0.22f)); lineTo(X(0.58f), Y(0.22f)); lineTo(X(0.66f), Y(0.32f)); lineTo(X(0.80f), Y(0.32f))
                    lineTo(X(0.80f), Y(0.44f)); lineTo(X(0.94f), Y(0.44f)); lineTo(X(0.94f), Y(0.70f)); lineTo(X(0.80f), Y(0.70f))
                    lineTo(X(0.80f), Y(0.80f)); lineTo(X(0.70f), Y(0.88f)); lineTo(X(0.30f), Y(0.88f)); lineTo(X(0.22f), Y(0.76f))
                    lineTo(X(0.12f), Y(0.76f)); lineTo(X(0.12f), Y(0.84f)); lineTo(X(0.04f), Y(0.84f)); lineTo(X(0.04f), Y(0.46f))
                    lineTo(X(0.12f), Y(0.46f)); lineTo(X(0.12f), Y(0.56f)); lineTo(X(0.22f), Y(0.56f)); lineTo(X(0.22f), Y(0.36f)); lineTo(X(0.30f), Y(0.32f)); close()
                }
                c.drawPath(e, p)
                c.drawLine(X(0.36f), Y(0.12f), X(0.52f), Y(0.12f), p); c.drawLine(X(0.44f), Y(0.12f), X(0.44f), Y(0.22f), p)
            }
            Icon.THROTTLE -> {
                c.save(); c.rotate(40f, X(0.5f), Y(0.5f))
                c.drawRoundRect(RectF(X(0.30f), Y(0.02f), X(0.70f), Y(0.98f)), s * 0.08f, s * 0.08f, p)
                for (k in 0..3) c.drawLine(X(0.38f), Y(0.36f + k * 0.14f), X(0.62f), Y(0.36f + k * 0.14f), p)
                c.restore()
            }
            Icon.INTAKE -> {
                c.drawRoundRect(RectF(X(0.18f), Y(0.02f), X(0.34f), Y(0.60f)), s * 0.08f, s * 0.08f, p)
                c.drawCircle(X(0.26f), Y(0.76f), s * 0.17f, p)
                for (k in 0..3) c.drawLine(X(0.50f), Y(0.12f + k * 0.13f), X(0.90f - (k % 2) * 0.12f), Y(0.12f + k * 0.13f), p)
            }
            null -> { // route
                c.drawCircle(X(0.78f), Y(0.22f), s * 0.13f, p)
                val route = Path().apply { moveTo(X(0.78f), Y(0.38f)); quadTo(X(0.70f), Y(0.62f), X(0.46f), Y(0.56f)); quadTo(X(0.20f), Y(0.50f), X(0.32f), Y(0.76f)); quadTo(X(0.38f), Y(0.92f), X(0.12f), Y(0.92f)) }
                c.drawPath(route, p)
                p.style = Paint.Style.FILL; c.drawCircle(X(0.12f), Y(0.92f), s * 0.07f, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    // ------------------------------------------------------------------ road stage

    /**
     * Dark road with a blue horizon glow, light trails spreading to the sides, moving lane marks
     * and the car from behind. Everything that moves is driven by the real speed. Illustrative only.
     */
    private fun stage(c: Canvas, r: RectF, f: CockpitFrame, carWidth: Float) {
        val w = r.width()
        val h = r.height()
        val vx = r.centerX()
        val horizon = r.top + h * 0.30f
        c.save(); c.clipRect(r)
        p.reset(); p.isAntiAlias = true
        // Horizon glow.
        // Elliptical so it fades out before the top edge of the stage (no hard edge behind the dials).
        val ry = (horizon - r.top) * 0.95f
        c.save(); c.scale(1f, ry / (w * 0.55f), vx, horizon)
        p.shader = RadialGradient(vx, horizon, w * 0.55f, Color.argb(130, 28, 108, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(vx, horizon, w * 0.55f, p)
        c.restore()
        p.shader = LinearGradient(r.left, 0f, r.right, 0f, intArrayOf(Color.TRANSPARENT, Color.argb(220, 90, 200, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        c.drawRect(r.left, horizon - h * 0.006f, r.right, horizon + h * 0.006f, p)
        // Ground.
        p.shader = LinearGradient(0f, horizon, 0f, r.bottom, Color.argb(110, 6, 14, 30), Color.argb(0, 2, 5, 12), Shader.TileMode.CLAMP)
        c.drawRect(r.left, horizon, r.right, r.bottom, p)
        val road = Path().apply {
            moveTo(vx - w * 0.03f, horizon); lineTo(vx + w * 0.03f, horizon); lineTo(vx + w * 0.30f, r.bottom); lineTo(vx - w * 0.30f, r.bottom); close()
        }
        p.shader = LinearGradient(0f, horizon, 0f, r.bottom, Color.rgb(16, 30, 56), Color.argb(200, 20, 34, 60), Shader.TileMode.CLAMP)
        c.drawPath(road, p); p.shader = null
        // Light trails spreading from the horizon to the sides (static) and their moving highlights.
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        val trails = listOf(0.30f to 0.72f, 0.50f to 0.46f, 0.80f to 0.30f)
        for ((i, tr) in trails.withIndex()) {
            for (side in floatArrayOf(-1f, 1f)) {
                val x1 = vx + side * w * (0.55f + i * 0.02f)
                val y1 = horizon + (r.bottom - horizon) * tr.second
                p.strokeWidth = h * (0.008f - i * 0.002f)
                p.shader = LinearGradient(vx + side * w * 0.04f, horizon, x1, y1, Color.argb(0, 64, 208, 255), Color.argb((230 - i * 60), 64, 208, 255), Shader.TileMode.CLAMP)
                c.drawLine(vx + side * w * 0.04f, horizon + h * 0.01f, x1, y1, p)
                p.shader = null
                val m = ((f.travelled / (8f + i * 3f)) + i * 0.3f) % 1f
                val t0 = m * m
                val t1 = (m + 0.12f).coerceAtMost(1f).let { it * it }
                val sx = vx + side * w * 0.04f
                p.color = Color.argb((200 * m).toInt(), 200, 240, 255); p.strokeWidth = h * 0.012f
                if ((f.speed ?: 0f) > 1f) c.drawLine(sx + (x1 - sx) * t0, horizon + (y1 - horizon) * t0, sx + (x1 - sx) * t1, horizon + (y1 - horizon) * t1, p)
            }
        }
        // Road edges and moving lane marks.
        p.strokeWidth = max(1.5f, w * 0.003f)
        p.color = Color.argb(200, 64, 208, 255)
        c.drawLine(vx - w * 0.03f, horizon, vx - w * 0.30f, r.bottom, p)
        c.drawLine(vx + w * 0.03f, horizon, vx + w * 0.30f, r.bottom, p)
        for (lane in floatArrayOf(-0.33f, 0.33f)) {
            for (i in 0 until 5) {
                val t0 = (i + f.roadPhase) / 5
                val t1 = (t0 + 0.07f).coerceAtMost(1f)
                fun xAt(t: Float) = vx + lane * (w * 0.03f + w * 0.27f * t * t)
                fun yAt(t: Float) = horizon + (r.bottom - horizon) * t * t
                p.color = Color.argb((40 + 170 * t0).toInt(), 255, 255, 255)
                p.strokeWidth = w * (0.0015f + 0.006f * t0)
                c.drawLine(xAt(t0), yAt(t0), xAt(t1), yAt(t1), p)
            }
        }
        p.style = Paint.Style.FILL
        val moving = (f.speed ?: 0f) > 1f
        val bob = if (moving) sin(f.travelled * 2.1f) * h * 0.005f * ((f.speed ?: 0f) / 120f).coerceAtMost(1f) else 0f
        val cwid = min(carWidth, h * 1.15f)
        car(c, vx, r.bottom - cwid * 0.34f + bob, cwid, f.braking || !moving)
        c.restore()
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


    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface?, align: Paint.Align) {
        p.reset(); p.isAntiAlias = true
        p.color = color; p.textSize = size; p.typeface = tf; p.textAlign = align
        c.drawText(s, x, y, p)
    }

    /** Like [text] but shrinks the size until it fits [maxW]. */
    private fun fitText(c: Canvas, s: String, x: Float, y: Float, size: Float, maxW: Float, color: Int, tf: Typeface?, align: Paint.Align) {
        p.reset(); p.isAntiAlias = true; p.typeface = tf
        var sz = size
        p.textSize = sz
        while (p.measureText(s) > maxW && sz > 6f) { sz *= 0.92f; p.textSize = sz }
        text(c, s, x, y, sz, color, tf, align)
    }
}
