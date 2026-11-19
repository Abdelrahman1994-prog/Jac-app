package com.jacdrive.app.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a gauge as a bitmap for Android Auto grid items (templates allow images, not free
 * drawing). 270° dial with a coloured value arc, an optional red zone, ticks and a needle.
 * The numeric value is shown by the host as the item title, so it is always legible.
 */
object GaugeBitmap {
    private const val START = 135f
    private const val SWEEP = 270f

    /**
     * @param fraction 0..1, or null when there is no value (empty dial, no needle).
     * @param dangerFrom fraction where the red zone starts, if any.
     */
    fun render(fraction: Float?, color: Int, dangerFrom: Float? = null, sizePx: Int = 256): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = sizePx.toFloat()
        val stroke = s * 0.085f
        val pad = stroke / 2 + s * 0.04f
        val rect = RectF(pad, pad, s - pad, s - pad)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = stroke }

        // Track.
        paint.color = Color.argb(70, 255, 255, 255)
        c.drawArc(rect, START, SWEEP, false, paint)

        // Red zone marker on the inside edge.
        if (dangerFrom != null) {
            val inner = RectF(rect).apply { inset(stroke * 1.1f, stroke * 1.1f) }
            paint.strokeWidth = stroke * 0.35f
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = Color.rgb(255, 59, 78)
            c.drawArc(inner, START + SWEEP * dangerFrom, SWEEP * (1 - dangerFrom), false, paint)
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
        }

        // Ticks.
        val cx = s / 2
        val cy = s / 2
        val rTick = rect.width() / 2 - stroke * 1.9f
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        for (i in 0..8) {
            val a = Math.toRadians((START + SWEEP * i / 8).toDouble())
            val major = i % 2 == 0
            tick.color = Color.argb(if (major) 200 else 110, 255, 255, 255)
            tick.strokeWidth = s * if (major) 0.022f else 0.014f
            val len = s * if (major) 0.07f else 0.04f
            c.drawLine(
                cx + cos(a).toFloat() * rTick, cy + sin(a).toFloat() * rTick,
                cx + cos(a).toFloat() * (rTick - len), cy + sin(a).toFloat() * (rTick - len), tick,
            )
        }

        if (fraction != null) {
            val f = fraction.coerceIn(0f, 1f)
            val inDanger = dangerFrom != null && f >= dangerFrom
            val valueColor = if (inDanger) Color.rgb(255, 59, 78) else color
            // Soft glow then the value arc.
            paint.color = Color.argb(70, Color.red(valueColor), Color.green(valueColor), Color.blue(valueColor))
            paint.strokeWidth = stroke * 1.9f
            if (f > 0.005f) c.drawArc(rect, START, SWEEP * f, false, paint)
            paint.color = valueColor
            paint.strokeWidth = stroke
            if (f > 0.005f) c.drawArc(rect, START, SWEEP * f, false, paint)

            // Needle.
            val a = Math.toRadians((START + SWEEP * f).toDouble())
            val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; strokeWidth = s * 0.03f; strokeCap = Paint.Cap.ROUND }
            val len = rTick - s * 0.02f
            c.drawLine(cx, cy, cx + cos(a).toFloat() * len, cy + sin(a).toFloat() * len, needle)
        }
        // Hub.
        val hub = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(12, 16, 22) }
        c.drawCircle(cx, cy, s * 0.06f, hub)
        hub.style = Paint.Style.STROKE
        hub.strokeWidth = s * 0.018f
        hub.color = Color.WHITE
        c.drawCircle(cx, cy, s * 0.06f, hub)
        return bmp
    }
}
