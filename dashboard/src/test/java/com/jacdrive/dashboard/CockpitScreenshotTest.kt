package com.jacdrive.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.GaugeLayout
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.dashboard.cockpit.CockpitLayoutContent
import com.jacdrive.dashboard.cockpit.CockpitUiState
import com.jacdrive.dashboard.cockpit.CockpitWarnings
import com.jacdrive.obd.demo.DemoDriveModel
import com.jacdrive.ui.cockpit.CockpitStyles
import com.jacdrive.ui.cockpit.cockpitBackground
import com.jacdrive.ui.components.DisplayUnits
import com.jacdrive.ui.theme.JacDriveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real cockpit composables on the JVM (no device needed) so themes, layouts and RTL can
 * be reviewed. Images are written to dashboard/build/outputs/roborazzi.
 * Run with: ./gradlew :dashboard:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w892dp-h412dp-land-xxhdpi")
class CockpitScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_000_000L

    /** Simulated mid-acceleration state from the demo model (clearly simulated data). */
    private fun demoSnapshot(): VehicleSnapshot {
        val model = DemoDriveModel()
        var s = model.step(0.1)
        repeat(900) { s = model.step(0.1) }
        while (s.phase != DemoDriveModel.Phase.ACCELERATE || s.speedKmh < 60) s = model.step(0.1)
        fun v(x: Double) = LiveValue(x, now, DataSource.SIMULATED)
        return VehicleSnapshot(
            mode = DataMode.DEMO,
            connection = ConnectionState.Disconnected,
            values = mapOf(
                VehicleMetric.SPEED to v(s.speedKmh), VehicleMetric.RPM to v(s.rpm),
                VehicleMetric.COOLANT_TEMP to v(s.coolantC), VehicleMetric.BATTERY_VOLTAGE to v(s.batteryV),
                VehicleMetric.ENGINE_LOAD to v(s.engineLoadPct), VehicleMetric.THROTTLE_POSITION to v(s.throttlePct),
                VehicleMetric.FUEL_LEVEL to v(s.fuelLevelPct), VehicleMetric.INTAKE_AIR_TEMP to v(s.intakeC),
                VehicleMetric.TRIP_DISTANCE to v(12.4), VehicleMetric.AVG_FUEL_CONSUMPTION to v(8.6),
            ),
            supportedMetrics = null,
            timestampMs = now,
        )
    }

    private fun render(name: String, prefs: CockpitPreferences, snapshot: VehicleSnapshot = demoSnapshot(), rtl: Boolean = false) {
        val state = CockpitUiState(snapshot, prefs, DisplayUnits(SpeedUnit.KMH, TemperatureUnit.CELSIUS))
        val style = CockpitStyles.resolve(prefs)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                JacDriveTheme(accent = style.accent) {
                    Box(Modifier.fillMaxSize().cockpitBackground(style, prefs.background)) {
                        CockpitLayoutContent(
                            state, style, now,
                            CockpitWarnings.evaluate(snapshot, prefs.redlineRpm, now),
                            Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun allThemesDualDial() {
        CockpitThemeId.entries.forEach { id ->
            render("theme_${id.name.lowercase()}", CockpitPreferences(themeId = id, animationsEnabled = false))
        }
    }

    @Test fun layouts() {
        render("layout_sport_center", CockpitPreferences(themeId = CockpitThemeId.SPORT_GT, layout = GaugeLayout.SPORT_CENTER, animationsEnabled = false))
        render("layout_digital", CockpitPreferences(themeId = CockpitThemeId.CYBER_BLUE, layout = GaugeLayout.DIGITAL, animationsEnabled = false))
    }

    @Test fun arabicRtl() {
        render("rtl_racing_red", CockpitPreferences(themeId = CockpitThemeId.RACING_RED, animationsEnabled = false), rtl = true)
    }

    @Test fun offlineShowsNoValues() {
        render("offline", CockpitPreferences(animationsEnabled = false), snapshot = VehicleSnapshot.offline(now))
    }
}
