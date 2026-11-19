package com.jacdrive.app

import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import com.jacdrive.app.auto.CarDeps
import com.jacdrive.app.auto.LiveScreen
import com.jacdrive.core.database.AlertDao
import com.jacdrive.core.database.AlertEventEntity
import com.jacdrive.core.database.DiagnosticCodeEntity
import com.jacdrive.core.database.DiagnosticDao
import com.jacdrive.core.database.DiagnosticSessionEntity
import com.jacdrive.core.database.SessionWithCodes
import com.jacdrive.core.model.AlertPreferences
import com.jacdrive.core.model.AppSettings
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.diagnostics.data.DtcDatabaseProvider
import com.jacdrive.obd.repository.VehicleDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Builds the Android Auto templates against the official TestCarContext. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CarScreensTest {

    private fun deps(snapshot: VehicleSnapshot): CarDeps {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return CarDeps(
            vehicleData = object : VehicleDataRepository { override val snapshot: StateFlow<VehicleSnapshot> = MutableStateFlow(snapshot) },
            settings = FakeSettings,
            diagnosticDao = object : DiagnosticDao {
                override suspend fun insertSession(session: DiagnosticSessionEntity) = 0L
                override suspend fun insertCodes(codes: List<DiagnosticCodeEntity>) = Unit
                override fun observeSessions(): Flow<List<SessionWithCodes>> = flowOf(emptyList())
                override fun observeSession(id: Long): Flow<SessionWithCodes?> = flowOf(null)
                override suspend fun delete(id: Long) = Unit
                override suspend fun deleteAll() = Unit
            },
            dtc = DtcDatabaseProvider(ctx),
            alertDao = object : AlertDao {
                override suspend fun insert(e: AlertEventEntity) = 0L
                override fun observeRecent(): Flow<List<AlertEventEntity>> = flowOf(emptyList())
                override suspend fun clear() = Unit
                override suspend fun trim() = Unit
            },
        )
    }

    private fun live(): VehicleSnapshot {
        fun v(x: Double) = LiveValue(x, System.currentTimeMillis(), DataSource.ECU)
        return VehicleSnapshot(
            DataMode.LIVE, ConnectionState.Connected("OBDII", "ISO 15765-4"),
            mapOf(VehicleMetric.COOLANT_TEMP to v(108.0), VehicleMetric.BATTERY_VOLTAGE to v(14.1), VehicleMetric.RPM to v(2100.0), VehicleMetric.ENGINE_LOAD to v(35.0)),
            setOf(VehicleMetric.COOLANT_TEMP, VehicleMetric.BATTERY_VOLTAGE, VehicleMetric.RPM, VehicleMetric.ENGINE_LOAD),
            System.currentTimeMillis(), milOn = true,
        )
    }

    @Test fun liveScreenShowsGaugeGridOfSupportedValues() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val t = LiveScreen(carContext, deps(live())).onGetTemplate() as GridTemplate
        val items = t.singleList!!.items.map { it as GridItem }
        // Only the 4 supported metrics are shown, each with a rendered gauge image.
        assertEquals(4, items.size)
        assertTrue(items.all { it.image != null })
        val text = items.joinToString { "${it.title}:${it.text}" }
        assertTrue(text, text.contains("14.1 V"))
        assertTrue(text, text.contains("2100 rpm"))
        assertTrue(text, !text.contains("Fuel"))
        assertEquals(2, t.actionStrip!!.actions.size)
    }

    @Test fun gaugeBitmapRendersEmptyAndFull() {
        val empty = com.jacdrive.app.auto.GaugeBitmap.render(null, android.graphics.Color.CYAN)
        val full = com.jacdrive.app.auto.GaugeBitmap.render(1f, android.graphics.Color.CYAN, dangerFrom = 0.75f)
        assertEquals(256, empty.width)
        assertEquals(256, full.height)
    }

    @Test fun demoDataIsLabelledOnTheCarScreen() {
        val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        val demo = live().copy(mode = DataMode.DEMO, values = live().values.mapValues { it.value.copy(source = DataSource.SIMULATED) })
        val t = LiveScreen(carContext, deps(demo)).onGetTemplate() as GridTemplate
        val text = t.singleList!!.items.joinToString { (it as GridItem).text.toString() } + t.title
        assertTrue(text, text.contains("DEMO") && text.contains("SIM"))
    }

    private object FakeSettings : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(AppSettings())
        override val cockpit: Flow<CockpitPreferences> = flowOf(CockpitPreferences())
        override val alerts: Flow<AlertPreferences> = flowOf(AlertPreferences())
        override suspend fun updateAlerts(transform: (AlertPreferences) -> AlertPreferences) = Unit
        override suspend fun setThemeMode(mode: com.jacdrive.core.model.ThemeMode) = Unit
        override suspend fun setSpeedUnit(unit: com.jacdrive.core.model.SpeedUnit) = Unit
        override suspend fun setTemperatureUnit(unit: com.jacdrive.core.model.TemperatureUnit) = Unit
        override suspend fun setPollingInterval(ms: Long) = Unit
        override suspend fun setDemoMode(enabled: Boolean) = Unit
        override suspend fun setOnboardingDone() = Unit
        override suspend fun setAdapter(address: String?, name: String?) = Unit
        override suspend fun setAutoReconnect(enabled: Boolean) = Unit
        override suspend fun setCockpitTheme(id: com.jacdrive.core.model.CockpitThemeId) = Unit
        override suspend fun setAccentOverride(argb: Long?) = Unit
        override suspend fun setGaugeLayout(layout: com.jacdrive.core.model.GaugeLayout) = Unit
        override suspend fun setDashboardBackground(background: com.jacdrive.core.model.DashboardBackground) = Unit
        override suspend fun setAnimationsEnabled(enabled: Boolean) = Unit
        override suspend fun setWidgetVisible(widget: com.jacdrive.core.model.DashboardWidget, visible: Boolean) = Unit
        override suspend fun setRedlineRpm(rpm: Int) = Unit
        override suspend fun setKeepScreenOn(enabled: Boolean) = Unit
    }
}
