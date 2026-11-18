package com.jacdrive.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jacdrive.ui.theme.JacFonts
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Branded intro: the dial sweeps to the redline and back, like an ignition self-test. */
@Composable
fun StartupScreen(onFinished: () -> Unit) {
    val sweep = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val accent = MaterialTheme.colorScheme.primary
    val red = Color(0xFFFF3B4E)

    LaunchedEffect(Unit) {
        val t = async { delay(350); text.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        sweep.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        sweep.animateTo(0.62f, tween(550, easing = FastOutSlowInEasing))
        t.await()
        delay(250)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF040507))
            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(170.dp)) {
                val d = size.minDimension
                val w = d * 0.045f
                val r = d / 2 - w
                val c = Offset(size.width / 2, size.height / 2)
                val tl = Offset(c.x - r, c.y - r)
                val s = Size(r * 2, r * 2)
                val start = 135f
                val total = 270f
                drawArc(Color.White.copy(alpha = 0.08f), start, total, false, tl, s, style = Stroke(w, cap = StrokeCap.Round))
                drawArc(red.copy(alpha = 0.9f), start + total * 0.82f, total * 0.18f, false, tl, s, style = Stroke(w * 0.5f))
                rotate(start, c) {
                    drawArc(
                        Brush.sweepGradient(0f to accent.copy(alpha = 0.2f), 0.75f to accent, center = c),
                        0f, total * sweep.value, false, tl, s, style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
                // Ticks.
                for (i in 0..27) {
                    val a = Math.toRadians((start + total * i / 27f).toDouble())
                    val major = i % 3 == 0
                    val ro = r - w * 1.4f
                    val ri = ro - if (major) d * 0.06f else d * 0.03f
                    drawLine(
                        Color.White.copy(alpha = if (major) 0.6f else 0.25f),
                        Offset(c.x + cos(a).toFloat() * ri, c.y + sin(a).toFloat() * ri),
                        Offset(c.x + cos(a).toFloat() * ro, c.y + sin(a).toFloat() * ro),
                        strokeWidth = if (major) d * 0.012f else d * 0.006f,
                    )
                }
                val na = Math.toRadians((start + total * sweep.value).toDouble())
                val tip = Offset(c.x + cos(na).toFloat() * r * 0.78f, c.y + sin(na).toFloat() * r * 0.78f)
                drawLine(accent.copy(alpha = 0.25f), c, tip, strokeWidth = d * 0.04f, cap = StrokeCap.Round)
                drawLine(Color.White, c, tip, strokeWidth = d * 0.014f, cap = StrokeCap.Round)
                drawCircle(Color(0xFF040507), d * 0.05f, c)
                drawCircle(accent, d * 0.05f, c, style = Stroke(d * 0.01f))
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.alpha(text.value).padding(top = (12 * (1 - text.value)).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "JAC DRIVE",
                    fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = 0.28.em,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.startup_tagline),
                    fontFamily = JacFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.em,
                    color = accent,
                )
            }
        }
        Text(
            "JAC J7 · 2026 ULTIMATE",
            fontFamily = JacFonts.Display, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.25.em,
            color = Color.White.copy(alpha = 0.35f * text.value),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
    }
}
