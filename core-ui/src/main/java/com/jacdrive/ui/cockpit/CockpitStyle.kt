package com.jacdrive.ui.cockpit

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.DashboardBackground

@Immutable
data class GaugeStyle(
    /** Total sweep of the dial in degrees. */
    val sweepDegrees: Float,
    /** Arc stroke width as a fraction of the dial diameter. */
    val arcWidthFraction: Float,
    /** Draw the value arc as discrete segments (LED style) instead of a continuous arc. */
    val segmented: Boolean,
    val segmentCount: Int = 48,
    val showNeedle: Boolean,
    val glow: Boolean,
    val showTickLabels: Boolean = true,
    /** Slowly rotating highlight on the bezel (only when animations are enabled). */
    val animatedBezel: Boolean = false,
    /** Outer bezel ring. */
    val bezel: Boolean = true,
)

@Immutable
data class CockpitStyle(
    val id: CockpitThemeId,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val defaultBackground: DashboardBackground,
    val panel: Color,
    val panelBorder: Color,
    val accent: Color,
    val accentSecondary: Color,
    val text: Color,
    val textDim: Color,
    val track: Color,
    val tick: Color,
    val ok: Color,
    val warning: Color,
    val critical: Color,
    val gauge: GaugeStyle,
)

object CockpitStyles {

    val JacOriginal = CockpitStyle(
        id = CockpitThemeId.JAC_ORIGINAL,
        backgroundTop = Color(0xFF0C1522),
        backgroundBottom = Color(0xFF030508),
        defaultBackground = DashboardBackground.GRADIENT,
        panel = Color(0x14FFFFFF),
        panelBorder = Color(0x22A9D8FF),
        accent = Color(0xFF36C2FF),
        accentSecondary = Color(0xFF1A6DFF),
        text = Color(0xFFF2F7FC),
        textDim = Color(0xFF8193A8),
        track = Color(0x1FFFFFFF),
        tick = Color(0xFFB9C7D6),
        ok = Color(0xFF2EE59D),
        warning = Color(0xFFFFB020),
        critical = Color(0xFFFF3B4E),
        gauge = GaugeStyle(sweepDegrees = 240f, arcWidthFraction = 0.035f, segmented = false, showNeedle = true, glow = true),
    )

    val RacingRed = CockpitStyle(
        id = CockpitThemeId.RACING_RED,
        backgroundTop = Color(0xFF140406),
        backgroundBottom = Color(0xFF000000),
        defaultBackground = DashboardBackground.CARBON,
        panel = Color(0x12FF2A3A),
        panelBorder = Color(0x33FF2A3A),
        accent = Color(0xFFFF1E2D),
        accentSecondary = Color(0xFFFF7A1A),
        text = Color(0xFFFFFFFF),
        textDim = Color(0xFF9C7C80),
        track = Color(0x22FF4050),
        tick = Color(0xFFE7C9CC),
        ok = Color(0xFF34E39A),
        warning = Color(0xFFFFB020),
        critical = Color(0xFFFFFFFF),
        gauge = GaugeStyle(sweepDegrees = 260f, arcWidthFraction = 0.06f, segmented = true, segmentCount = 44, showNeedle = false, glow = true),
    )

    val CyberBlue = CockpitStyle(
        id = CockpitThemeId.CYBER_BLUE,
        backgroundTop = Color(0xFF0A1A44),
        backgroundBottom = Color(0xFF02040F),
        defaultBackground = DashboardBackground.GRID,
        panel = Color(0x1400E5FF),
        panelBorder = Color(0x4000E5FF),
        accent = Color(0xFF00E5FF),
        accentSecondary = Color(0xFF7C4DFF),
        text = Color(0xFFE6FBFF),
        textDim = Color(0xFF6E8DB8),
        track = Color(0x2200E5FF),
        tick = Color(0xFF8FE9FF),
        ok = Color(0xFF00FFA3),
        warning = Color(0xFFFFC233),
        critical = Color(0xFFFF2E88),
        gauge = GaugeStyle(sweepDegrees = 270f, arcWidthFraction = 0.045f, segmented = true, segmentCount = 60, showNeedle = true, glow = true),
    )

    val CarbonBlack = CockpitStyle(
        id = CockpitThemeId.CARBON_BLACK,
        backgroundTop = Color(0xFF000000),
        backgroundBottom = Color(0xFF000000),
        defaultBackground = DashboardBackground.PURE_BLACK,
        panel = Color(0x0DFFFFFF),
        panelBorder = Color(0x1FFFFFFF),
        accent = Color(0xFFF5F5F5),
        accentSecondary = Color(0xFF9E9E9E),
        text = Color(0xFFFFFFFF),
        textDim = Color(0xFF7A7A7A),
        track = Color(0x1AFFFFFF),
        tick = Color(0xFFBDBDBD),
        ok = Color(0xFFBDBDBD),
        warning = Color(0xFFFFB020),
        critical = Color(0xFFFF3B30),
        gauge = GaugeStyle(sweepDegrees = 270f, arcWidthFraction = 0.018f, segmented = false, showNeedle = true, glow = false, bezel = false),
    )

    val SportGt = CockpitStyle(
        id = CockpitThemeId.SPORT_GT,
        backgroundTop = Color(0xFF1A1610),
        backgroundBottom = Color(0xFF050404),
        defaultBackground = DashboardBackground.CARBON,
        panel = Color(0x14FFB300),
        panelBorder = Color(0x33FFB300),
        accent = Color(0xFFFFB300),
        accentSecondary = Color(0xFFFF5A1F),
        text = Color(0xFFFFF8EC),
        textDim = Color(0xFFA08F72),
        track = Color(0x1FFFD180),
        tick = Color(0xFFEBDDC4),
        ok = Color(0xFF3BE38F),
        warning = Color(0xFFFF8F00),
        critical = Color(0xFFFF3D3D),
        gauge = GaugeStyle(sweepDegrees = 250f, arcWidthFraction = 0.05f, segmented = false, showNeedle = true, glow = true, animatedBezel = true),
    )

    val all: List<CockpitStyle> = listOf(JacOriginal, RacingRed, CyberBlue, CarbonBlack, SportGt)

    fun forId(id: CockpitThemeId): CockpitStyle = all.first { it.id == id }

    /** Resolves the style with the user's accent override applied. */
    fun resolve(prefs: CockpitPreferences): CockpitStyle {
        val base = forId(prefs.themeId)
        val override = prefs.accentOverride ?: return base
        return base.copy(accent = Color(override))
    }

    /** Accent presets offered in the customisation screen. */
    val accentPresets: List<Long> = listOf(
        0xFF36C2FF, 0xFF00E5FF, 0xFF2EE59D, 0xFFB2FF59, 0xFFFFB300,
        0xFFFF7A1A, 0xFFFF1E2D, 0xFFFF2E88, 0xFF7C4DFF, 0xFFF5F5F5,
    )
}
