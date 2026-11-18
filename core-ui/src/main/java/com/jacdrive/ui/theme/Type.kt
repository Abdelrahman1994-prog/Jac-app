package com.jacdrive.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jacdrive.ui.R

object JacFonts {
    /** Body/UI text: Cairo covers both Arabic and Latin. */
    val Body = FontFamily(
        Font(R.font.cairo_regular, FontWeight.Normal),
        Font(R.font.cairo_bold, FontWeight.Bold),
        Font(R.font.cairo_bold, FontWeight.SemiBold),
    )

    /** Instrument numerals and display headings: Rajdhani's condensed, technical figures. */
    val Display = FontFamily(
        Font(R.font.rajdhani_medium, FontWeight.Medium),
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
    )
}

internal val JacTypography: Typography = Typography().let { base ->
    fun TextStyle.body() = copy(fontFamily = JacFonts.Body)
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = JacFonts.Display, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.body().copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.body().copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.body().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.body().copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.body().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.body().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.body().copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em),
    )
}

/** Styles for instrument readouts, independent of the Material type scale. */
object Readout {
    val Hero = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 96.sp)
    val Large = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
    val Medium = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 32.sp)
    val Small = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 22.sp)
    val Unit = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.08.em)
    val Caption = TextStyle(fontFamily = JacFonts.Body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.08.em)
}
