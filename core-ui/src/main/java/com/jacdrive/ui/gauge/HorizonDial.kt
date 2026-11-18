package com.jacdrive.ui.gauge

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacdrive.ui.cockpit.CockpitStyle
import com.jacdrive.ui.theme.JacFonts
import kotlin.math.cos
import kotlin.math.sin

/** A small arc gauge drawn inside the bottom of a [HorizonDial] (fuel E–F, coolant C–H). */
data class SubGauge(
    val fraction: Float?,
    val startLabel: String,
    val endLabel: String,
    val icon: ImageVector,
    val caption: String?,
    val alert: Boolean = false,
)

/**
 * Minimal ring dial: thin track, one coloured value arc, light numerals and an optional sub-gauge
 * along the inner bottom edge. Drawn on a Canvas; never mirrored in RTL (dials always run clockwise).
 */
@Composable
fun HorizonDial(
    fraction: Float?,
    color: Color,
    value: String,
    unit: String,
    style: CockpitStyle,
    modifier: Modifier = Modifier,
    sub: SubGauge? = null,
    animate: Boolean = true,
    dimmed: Boolean = false,
) {
    val spec = if (animate) spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow) else snap()
    val f by animateFloatAsState(fraction?.coerceIn(0f, 1f) ?: 0f, spec, label = "dial")
    val subF by animateFloatAsState(sub?.fraction?.coerceIn(0f, 1f) ?: 0f, spec, label = "sub")
    val subColor = if (sub?.alert == true) style.critical else style.tick

    BoxWithConstraints(modifier.aspectRatio(1f).alpha(if (dimmed) 0.5f else 1f), contentAlignment = Alignment.Center) {
        val d = if (maxWidth < maxHeight) maxWidth else maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val size0 = size.minDimension
            val c = Offset(size.width / 2, size.height / 2)
            val w = size0 * 0.024f
            val r = size0 / 2 * 0.92f - w

            // Soft face and outer hairline.
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent), c, r), r, c)
            drawCircle(style.track.copy(alpha = 0.6f), r + w * 1.6f, c, style = Stroke(size0 * 0.004f))

            // Main ring: 290° with the gap at the bottom.
            val start = 125f
            val sweep = 290f
            arc(style.track, c, r, start, sweep, w)
            if (fraction != null && f > 0.002f) {
                arc(color.copy(alpha = 0.18f), c, r, start, sweep * f, w * 2.6f)
                arc(color, c, r, start, sweep * f, w)
            }

            // Sub-gauge across the inner bottom: fills from the start label (left) to the end label (right).
            if (sub != null) {
                val sr = r * 0.80f
                val sw = size0 * 0.009f
                val subStart = 145f
                val subSweep = -110f
                arc(style.track, c, sr, subStart + subSweep, -subSweep, sw)
                if (sub.fraction != null) {
                    arc(subColor, c, sr, subStart + subSweep * subF, -subSweep * subF, sw)
                }
            }
        }

        // Centre readout.
        val labelStyle = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Medium, fontSize = (d.value * 0.045f).sp, textDirection = TextDirection.Ltr)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = -(d * 0.04f))) {
            Text(
                value,
                style = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Light, fontSize = (d.value * 0.24f).sp, lineHeight = (d.value * 0.24f).sp, textDirection = TextDirection.Ltr),
                color = style.text,
            )
            Text(unit, style = labelStyle, color = style.textDim)
        }

        if (sub != null) {
            val small = labelStyle.copy(fontSize = (d.value * 0.042f).sp)
            Box(Modifier.fillMaxSize()) {
                // E / F (C / H) labels at the ends of the sub-arc.
                Text(sub.startLabel, style = small, color = style.textDim, modifier = Modifier.align(Alignment.BottomStart).offset(x = d * 0.25f, y = -(d * 0.20f)))
                Text(sub.endLabel, style = small, color = style.textDim, modifier = Modifier.align(Alignment.BottomEnd).offset(x = -(d * 0.25f), y = -(d * 0.20f)))
                Row(Modifier.align(Alignment.BottomCenter).offset(y = -(d * 0.16f)), verticalAlignment = Alignment.CenterVertically) {
                    Icon(sub.icon, null, tint = subColor, modifier = Modifier.size(d * 0.05f))
                    if (sub.caption != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(sub.caption, style = small, color = style.text)
                    }
                }
            }
        }
    }
}

private fun DrawScope.arc(color: Color, c: Offset, r: Float, start: Float, sweep: Float, width: Float) {
    drawArc(color, start, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(width, cap = StrokeCap.Round))
}
