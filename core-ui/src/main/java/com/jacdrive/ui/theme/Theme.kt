package com.jacdrive.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.jacdrive.core.model.ThemeMode

/** Semantic status colours shared by every screen (not tied to a cockpit theme). */
data class StatusColors(
    val ok: Color,
    val warning: Color,
    val critical: Color,
    val info: Color,
    val demo: Color,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Color(0xFF2EE59D), Color(0xFFFFB020), Color(0xFFFF3B4E), Color(0xFF3FB8FF), Color(0xFFB57BFF))
}

private fun darkScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF051018),
    primaryContainer = accent.copy(alpha = 0.18f).compositeOver(Color(0xFF0E1218)),
    onPrimaryContainer = Color(0xFFE9F6FF),
    secondary = Color(0xFF9AA6B8),
    onSecondary = Color(0xFF0A0D12),
    secondaryContainer = Color(0xFF1C222B),
    onSecondaryContainer = Color(0xFFDCE3EE),
    tertiary = Color(0xFFFFB020),
    background = Color(0xFF040812),
    onBackground = Color(0xFFE8F1FF),
    surface = Color(0xFF070D1A),
    onSurface = Color(0xFFE8F1FF),
    surfaceVariant = Color(0xFF122036),
    onSurfaceVariant = Color(0xFF8AA0BF),
    surfaceContainerLowest = Color(0xFF03060D),
    surfaceContainerLow = Color(0xFF0A1222),
    surfaceContainer = Color(0xFF0E1830),
    surfaceContainerHigh = Color(0xFF13203A),
    surfaceContainerHighest = Color(0xFF1A2946),
    outline = Color(0xFF2A3A57),
    outlineVariant = Color(0xFF1A2640),
    error = Color(0xFFFF4D5E),
)

private fun lightScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent.copy(red = accent.red * 0.75f, green = accent.green * 0.75f, blue = accent.blue * 0.75f),
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.16f).compositeOver(Color.White),
    onPrimaryContainer = Color(0xFF06131C),
    secondary = Color(0xFF4F5B6B),
    secondaryContainer = Color(0xFFE2E7EE),
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF0D1117),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFFE6EAF0),
    onSurfaceVariant = Color(0xFF55606F),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF2F6),
    surfaceContainerHighest = Color(0xFFE6EAF0),
    outline = Color(0xFFC9D0DA),
    outlineVariant = Color(0xFFDDE2E9),
)

/**
 * Root theme. The app accent follows the selected cockpit theme so the whole product feels like
 * one instrument cluster; the cockpit screen itself additionally uses [com.jacdrive.ui.cockpit.CockpitStyle].
 */
@Composable
fun JacDriveTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accent: Color = Color(0xFF36C2FF),
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val status = if (dark) {
        StatusColors(Color(0xFF2EE59D), Color(0xFFFFB020), Color(0xFFFF3B4E), Color(0xFF3FB8FF), Color(0xFFB57BFF))
    } else {
        StatusColors(Color(0xFF0E9F6E), Color(0xFFC77800), Color(0xFFD71F33), Color(0xFF0B79C9), Color(0xFF7B3FE4))
    }
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(accent) else lightScheme(accent),
            typography = JacTypography,
            content = content,
        )
    }
}

/** Accent used everywhere in Sport mode (replaces the blue/cyan accent). */
val SportAccent = Color(0xFFFF4D2E)

object JacTheme {
    val status: StatusColors @Composable get() = LocalStatusColors.current
}
