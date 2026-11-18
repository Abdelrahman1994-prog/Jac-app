package com.jacdrive.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.jacdrive.diagnostics.data.DtcDatabase
import com.jacdrive.obd.dtc.Monitor
import com.jacdrive.obd.dtc.MonitorState
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.theme.JacDriveTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders fault-code rows and readiness in both languages. Output: diagnostics/build/outputs/roborazzi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val db = DtcDatabase(File("src/main/assets/dtc_database.json").readText())

    private fun render(name: String) {
        compose.setContent {
            JacDriveTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    JacCard {
                        Text(androidx.compose.ui.res.stringResource(R.string.diag_stored), style = MaterialTheme.typography.titleSmall)
                        listOf("P0301", "P0217", "P0420", "P0442", "P1A23").forEach { DtcRow(db.lookup(it)) {} }
                    }
                    JacCard(Modifier.padding(top = 12.dp)) {
                        ReadinessGrid(
                            linkedMapOf(
                                Monitor.MISFIRE to MonitorState.COMPLETE, Monitor.FUEL_SYSTEM to MonitorState.COMPLETE,
                                Monitor.CATALYST to MonitorState.INCOMPLETE, Monitor.EVAP_SYSTEM to MonitorState.INCOMPLETE,
                                Monitor.OXYGEN_SENSOR to MonitorState.COMPLETE, Monitor.OXYGEN_SENSOR_HEATER to MonitorState.COMPLETE,
                            ),
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    @Test fun english() = render("dtc_en")

    @Config(sdk = [34], qualifiers = "ar-w411dp-h891dp-xxhdpi")
    @Test fun arabic() = render("dtc_ar")
}
