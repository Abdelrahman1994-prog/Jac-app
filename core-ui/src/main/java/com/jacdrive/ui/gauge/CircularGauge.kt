package com.jacdrive.ui.gauge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import com.jacdrive.ui.cockpit.CockpitStyle
import com.jacdrive.ui.theme.JacFonts
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Instrument-cluster style circular gauge drawn entirely on a Canvas.
 *
 * @param value current value, or null when there is no data (needle parks, arc stays empty).
 * @param majorStep value distance between labelled ticks.
 * @param labelDivisor labels are shown as value / divisor (e.g. 1000 for an RPM x1000 dial).
 * @param dangerFrom start of the red zone, if any.
 * @param dimmed render at reduced intensity (stale data).
 */
@Composable
fun CircularGauge(
    value: Float?,
    min: Float,
    max: Float,
    style: CockpitStyle,
    modifier: Modifier = Modifier,
    majorStep: Float,
    minorDivisions: Int = 4,
    labelDivisor: Float = 1f,
    dangerFrom: Float? = null,
    animate: Boolean = true,
    dimmed: Boolean = false,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val fraction = value?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) } ?: 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (animate) spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) else snap(),
        label = "gauge",
    )
    val bezelAngle: State<Float>? = if (animate && style.gauge.animatedBezel) {
        rememberInfiniteTransition(label = "bezel").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
            label = "bezelAngle",
        )
    } else null
    val dangerFraction = dangerFrom?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) }
    val measurer = rememberTextMeasurer()
    val labels = remember(min, max, majorStep, labelDivisor) {
        val count = ((max - min) / majorStep).roundToInt()
        (0..count).map { i ->
            val v = (min + i * majorStep) / labelDivisor
            if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
        }
    }

    Box(modifier.aspectRatio(1f).alpha(if (dimmed) 0.45f else 1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension
            val c = Offset(size.width / 2, size.height / 2)
            val g = style.gauge
            val arcW = d * g.arcWidthFraction
            val r = d / 2 * 0.90f - arcW / 2
            val start = 90f + (360f - g.sweepDegrees) / 2f
            val sweep = g.sweepDegrees
            val arcTopLeft = Offset(c.x - r, c.y - r)
            val arcSize = Size(r * 2, r * 2)

            // Dial face and bezel.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(style.panel, Color.Transparent), center = c, radius = d / 2,
                ),
                radius = d / 2 * 0.98f, center = c,
            )
            if (g.bezel) {
                drawCircle(style.panelBorder, radius = d / 2 * 0.985f, center = c, style = Stroke(d * 0.006f))
            }
            bezelAngle?.let { a ->
                rotate(a.value, c) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent, 0.12f to style.accent.copy(alpha = 0.55f), 0.13f to Color.Transparent,
                            center = c,
                        ),
                        startAngle = 0f, sweepAngle = 50f, useCenter = false,
                        topLeft = Offset(c.x - d / 2 * 0.985f, c.y - d / 2 * 0.985f),
                        size = Size(d * 0.985f, d * 0.985f),
                        style = Stroke(d * 0.012f, cap = StrokeCap.Round),
                    )
                }
            }

            if (g.segmented) {
                drawSegmentedArc(style, start, sweep, animated, dangerFraction, arcTopLeft, arcSize, arcW)
            } else {
                drawContinuousArc(style, c, start, sweep, animated, dangerFraction, arcTopLeft, arcSize, arcW)
            }

            // Ticks.
            val majors = labels.size - 1
            val totalTicks = majors * minorDivisions
            val tickOuter = r - arcW * 1.3f
            for (i in 0..totalTicks) {
                val t = i / totalTicks.toFloat()
                val isMajor = i % minorDivisions == 0
                val angle = Math.toRadians((start + t * sweep).toDouble())
                val len = if (isMajor) d * 0.055f else d * 0.024f
                val inDanger = dangerFraction != null && t >= dangerFraction
                val color = when {
                    inDanger -> style.critical
                    isMajor -> style.tick
                    else -> style.tick.copy(alpha = 0.45f)
                }
                val o = Offset(c.x + cos(angle).toFloat() * tickOuter, c.y + sin(angle).toFloat() * tickOuter)
                val iP = Offset(c.x + cos(angle).toFloat() * (tickOuter - len), c.y + sin(angle).toFloat() * (tickOuter - len))
                drawLine(color, iP, o, strokeWidth = if (isMajor) d * 0.008f else d * 0.004f, cap = StrokeCap.Round)
            }

            // Labels.
            if (g.showTickLabels) {
                val fontPx = d * 0.058f
                val textStyle = TextStyle(
                    fontFamily = JacFonts.Display, fontWeight = FontWeight.SemiBold,
                    fontSize = TextUnit(fontPx / density / fontScale, androidx.compose.ui.unit.TextUnitType.Sp),
                )
                val labelR = tickOuter - d * 0.055f - fontPx * 0.85f
                labels.forEachIndexed { i, label ->
                    val t = i / majors.toFloat()
                    val angle = Math.toRadians((start + t * sweep).toDouble())
                    val inDanger = dangerFraction != null && t >= dangerFraction
                    val layout = measurer.measure(label, textStyle.copy(color = if (inDanger) style.critical else style.textDim))
                    val p = Offset(c.x + cos(angle).toFloat() * labelR, c.y + sin(angle).toFloat() * labelR)
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y - layout.size.height / 2))
                }
            }

            // Red zone marker inside the ticks.
            if (dangerFraction != null) {
                val rz = tickOuter + arcW * 0.55f
                drawArc(
                    color = style.critical.copy(alpha = 0.85f),
                    startAngle = start + dangerFraction * sweep, sweepAngle = (1 - dangerFraction) * sweep,
                    useCenter = false, topLeft = Offset(c.x - rz, c.y - rz), size = Size(rz * 2, rz * 2),
                    style = Stroke(arcW * 0.35f),
                )
            }

            if (g.showNeedle) drawNeedle(style, c, d, start + animated * sweep, tickOuter, value != null, dangerFraction?.let { animated >= it } == true)
        }
        center()
    }
}

private fun DrawScope.drawContinuousArc(
    style: CockpitStyle, c: Offset, start: Float, sweep: Float, f: Float, dangerFraction: Float?,
    topLeft: Offset, size: Size, w: Float,
) {
    drawArc(style.track, start, sweep, false, topLeft, size, style = Stroke(w, cap = StrokeCap.Round))
    if (f <= 0.001f) return
    val valueSweep = f * sweep
    if (style.gauge.glow) {
        drawArc(style.accent.copy(alpha = 0.07f), start, valueSweep, false, topLeft, size, style = Stroke(w * 4.2f, cap = StrokeCap.Round))
        drawArc(style.accent.copy(alpha = 0.14f), start, valueSweep, false, topLeft, size, style = Stroke(w * 2.2f, cap = StrokeCap.Round))
    }
    rotate(start, c) {
        drawArc(
            brush = Brush.sweepGradient(0f to style.accentSecondary, (sweep / 360f) to style.accent, center = c),
            startAngle = 0f, sweepAngle = valueSweep, useCenter = false,
            topLeft = topLeft, size = size, style = Stroke(w, cap = StrokeCap.Round),
        )
    }
    if (dangerFraction != null && f > dangerFraction) {
        drawArc(
            style.critical, start + dangerFraction * sweep, (f - dangerFraction) * sweep, false, topLeft, size,
            style = Stroke(w, cap = StrokeCap.Butt),
        )
    }
}

private fun DrawScope.drawSegmentedArc(
    style: CockpitStyle, start: Float, sweep: Float, f: Float, dangerFraction: Float?,
    topLeft: Offset, size: Size, w: Float,
) {
    val n = style.gauge.segmentCount
    val seg = sweep / n
    val gap = seg * 0.30f
    for (i in 0 until n) {
        val t = (i + 0.5f) / n
        val lit = t <= f
        val inDanger = dangerFraction != null && t >= dangerFraction
        val segStart = start + i * seg + gap / 2
        val color = when {
            !lit && inDanger -> style.critical.copy(alpha = 0.18f)
            !lit -> style.track
            inDanger -> style.critical
            else -> lerp(style.accentSecondary, style.accent, i / n.toFloat())
        }
        if (lit && style.gauge.glow) {
            drawArc(color.copy(alpha = 0.22f), segStart, seg - gap, false, topLeft, size, style = Stroke(w * 2.2f))
        }
        drawArc(color, segStart, seg - gap, false, topLeft, size, style = Stroke(w))
    }
}

private fun DrawScope.drawNeedle(
    style: CockpitStyle, c: Offset, d: Float, angleDeg: Float, length: Float, hasValue: Boolean, inDanger: Boolean,
) {
    val a = Math.toRadians(angleDeg.toDouble())
    val color = when {
        !hasValue -> style.textDim
        inDanger -> style.critical
        else -> style.accent
    }
    val tail = Offset(c.x - cos(a).toFloat() * d * 0.06f, c.y - sin(a).toFloat() * d * 0.06f)
    val tip = Offset(c.x + cos(a).toFloat() * (length - d * 0.01f), c.y + sin(a).toFloat() * (length - d * 0.01f))
    if (style.gauge.glow && hasValue) {
        drawLine(color.copy(alpha = 0.18f), tail, tip, strokeWidth = d * 0.035f, cap = StrokeCap.Round)
    }
    drawLine(color, tail, tip, strokeWidth = d * 0.012f, cap = StrokeCap.Round)
    drawCircle(Color.Black, radius = d * 0.05f, center = c)
    drawCircle(color, radius = d * 0.05f, center = c, style = Stroke(d * 0.008f))
}
