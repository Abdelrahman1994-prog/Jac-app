package com.jacdrive.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.jacdrive.core.util.Num
import com.jacdrive.ui.chart.BarChart
import com.jacdrive.ui.chart.BarDatum
import com.jacdrive.ui.chart.RollingLineChart
import com.jacdrive.ui.chart.TimedValue
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.theme.JacDriveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChartsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun render(name: String, rtl: Boolean) {
        val days = listOf("12", "13", "14", "15", "16", "17", "18")
        val km = listOf(24.0, 0.0, 61.5, 12.2, null, 88.0, 35.4)
        val now = 60_000L
        val rpm = (0..240).map { TimedValue(it * 250L, 1800 + 900 * sin(it / 12.0) + if (it in 150..170) 2200.0 else 0.0) }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                JacDriveTheme {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                        JacCard {
                            Text("Distance", style = MaterialTheme.typography.titleSmall)
                            BarChart(days.zip(km).map { BarDatum(it.first, it.second) }, MaterialTheme.colorScheme.primary, { "${Num.one(it)} km" })
                        }
                        Spacer(Modifier.height(12.dp))
                        JacCard {
                            RollingLineChart("Engine speed", rpm, 0.0, 8000.0, now, MaterialTheme.colorScheme.primary, "2,140")
                            Spacer(Modifier.height(12.dp))
                            RollingLineChart("Throttle", rpm.map { TimedValue(it.tMs, (it.value - 900) / 60) }, 0.0, 100.0, now, MaterialTheme.colorScheme.primary, "21 %")
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun ltr() = render("charts_ltr", false)
    @Test fun rtl() = render("charts_rtl", true)
}
