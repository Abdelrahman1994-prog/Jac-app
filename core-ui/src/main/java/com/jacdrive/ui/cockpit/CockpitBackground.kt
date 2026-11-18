package com.jacdrive.ui.cockpit

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import com.jacdrive.core.model.DashboardBackground
import kotlin.math.max

/** Paints the cockpit background. Patterns are rendered once per size and cached. */
fun Modifier.cockpitBackground(style: CockpitStyle, choice: DashboardBackground): Modifier = drawWithCache {
    val kind = if (choice == DashboardBackground.THEME_DEFAULT) style.defaultBackground else choice
    val vertical = Brush.verticalGradient(listOf(style.backgroundTop, style.backgroundBottom))
    val glow = Brush.radialGradient(
        colors = listOf(style.accent.copy(alpha = 0.10f), Color.Transparent),
        center = Offset(size.width / 2, size.height * 0.55f),
        radius = max(size.width, size.height) * 0.6f,
    )
    val vignette = Brush.radialGradient(
        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
        center = Offset(size.width / 2, size.height / 2),
        radius = max(size.width, size.height) * 0.75f,
    )
    val carbon = if (kind == DashboardBackground.CARBON) carbonBrush(density) else null
    val gridStep = 28f * density

    onDrawBehind {
        when (kind) {
            DashboardBackground.PURE_BLACK -> drawRect(Color.Black)
            DashboardBackground.GRADIENT, DashboardBackground.THEME_DEFAULT -> {
                drawRect(vertical)
                drawRect(glow)
            }
            DashboardBackground.CARBON -> {
                drawRect(Color(0xFF050505))
                drawRect(carbon!!, alpha = 0.9f)
                drawRect(vertical, alpha = 0.55f)
                drawRect(glow)
            }
            DashboardBackground.GRID -> {
                drawRect(vertical)
                val line = style.accent.copy(alpha = 0.06f)
                var x = 0f
                while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += gridStep }
                var y = 0f
                while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += gridStep }
                drawRect(glow)
            }
        }
        if (kind != DashboardBackground.PURE_BLACK) drawRect(vignette)
    }
}

/** A small twill-weave tile repeated across the surface. */
private fun carbonBrush(density: Float): ShaderBrush {
    val cell = (6 * density).toInt().coerceAtLeast(4)
    val tileSize = cell * 2
    val bitmap = ImageBitmap(tileSize, tileSize)
    val canvas = Canvas(bitmap)
    val dark = Paint().apply { color = Color(0xFF0B0B0C) }
    val light = Paint().apply {
        shader = androidx.compose.ui.graphics.LinearGradientShader(
            from = Offset.Zero, to = Offset(cell.toFloat(), cell.toFloat()),
            colors = listOf(Color(0xFF1E1F22), Color(0xFF111214)),
        )
    }
    canvas.drawRect(0f, 0f, tileSize.toFloat(), tileSize.toFloat(), dark)
    canvas.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), light)
    canvas.drawRect(cell.toFloat(), cell.toFloat(), tileSize.toFloat(), tileSize.toFloat(), light)
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}
