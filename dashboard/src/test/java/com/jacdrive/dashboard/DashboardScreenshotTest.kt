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
import com.jacdrive.core.database.DiagnosticSessionEntity
import com.jacdrive.core.database.SessionWithCodes
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.dashboard.dash.DashboardContent
import com.jacdrive.dashboard.dash.DashboardUiState
import com.jacdrive.obd.history.HistoryPoint
import com.jacdrive.obd.history.HistoryState
import com.jacdrive.ui.components.appBackground
import com.jacdrive.ui.theme.JacDriveTheme
import com.jacdrive.ui.theme.SportAccent
import androidx.compose.ui.graphics.Color
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/** Phone-portrait renders of the redesigned dashboard. Output: dashboard/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h1400dp-port-xxhdpi")
class DashboardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 10_000_000L

    private fun snapshot(mode: DataMode, coolant: Double): VehicleSnapshot {
        val src = if (mode == DataMode.DEMO) DataSource.SIMULATED else DataSource.ECU
        fun v(x: Double) = LiveValue(x, now, src)
        return VehicleSnapshot(
            mode = mode,
            connection = if (mode == DataMode.LIVE) ConnectionState.Connected("OBDII", "ISO 15765-4") else ConnectionState.Disconnected,
            values = mapOf(
                VehicleMetric.SPEED to v(88.0), VehicleMetric.RPM to v(2650.0),
                VehicleMetric.COOLANT_TEMP to v(coolant), VehicleMetric.BATTERY_VOLTAGE to v(14.2),
                VehicleMetric.ENGINE_LOAD to v(32.0), VehicleMetric.THROTTLE_POSITION to v(18.0),
                VehicleMetric.INTAKE_AIR_TEMP to v(36.0),
                VehicleMetric.TRIP_DISTANCE to LiveValue(124.6, now, DataSource.CALCULATED),
            ),
            // Fuel level and average consumption deliberately unsupported → must show N/A.
            supportedMetrics = if (mode == DataMode.LIVE) VehicleMetric.entries.toSet() - VehicleMetric.FUEL_LEVEL - VehicleMetric.AVG_FUEL_CONSUMPTION else null,
            timestampMs = now,
        )
    }

    private fun history(mode: DataMode) = HistoryState(
        series = mapOf(
            VehicleMetric.SPEED to (0 until 60).map { HistoryPoint(now - (60 - it) * 1000L, 60 + 30 * sin(it / 8.0)) },
            VehicleMetric.RPM to (0 until 60).map { HistoryPoint(now - (60 - it) * 1000L, 2200 + 700 * sin(it / 6.0)) },
            VehicleMetric.COOLANT_TEMP to (0 until 60).map { HistoryPoint(now - (60 - it) * 1000L, 86 + it * 0.05) },
        ),
        mode = mode,
    )

    private fun scan(codes: Int, mil: Boolean) = SessionWithCodes(
        DiagnosticSessionEntity(
            id = 1, timestampMs = now - 3_600_000, kind = "SCAN", milOn = mil, reportedDtcCount = codes,
            permanentSupported = false, readiness = null, failedReads = null, protocol = null, adapterName = null, mileageKm = null,
        ),
        emptyList(),
    )

    private fun render(name: String, state: DashboardUiState, rtl: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                JacDriveTheme(accent = if (state.cockpit.sportMode) SportAccent else Color(0xFF3FD4FF)) {
                    Box(Modifier.fillMaxSize().appBackground(true, if (state.cockpit.sportMode) SportAccent else Color(0xFF3FD4FF))) {
                        DashboardContent(state, now, {}, {}, {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/dashboard_$name.png")
    }

    @Test fun liveHealthy() = render(
        "live_ok",
        DashboardUiState(snapshot(DataMode.LIVE, 88.0), cockpit = CockpitPreferences(animationsEnabled = false), sessions = listOf(scan(0, false)), history = history(DataMode.LIVE)),
    )

    @Config(qualifiers = "+ar")
    @Test fun liveHotSportArabic() = render(
        "live_hot_sport_ar",
        DashboardUiState(snapshot(DataMode.LIVE, 108.0), cockpit = CockpitPreferences(animationsEnabled = false, sportMode = true), sessions = listOf(scan(2, true)), history = history(DataMode.LIVE)),
        rtl = true,
    )

    @Test fun offline() = render("offline", DashboardUiState(cockpit = CockpitPreferences(animationsEnabled = false)))
}
