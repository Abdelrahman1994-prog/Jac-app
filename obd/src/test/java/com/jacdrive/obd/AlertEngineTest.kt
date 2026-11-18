package com.jacdrive.obd

import com.jacdrive.core.model.AlertPreferences
import com.jacdrive.core.model.AlertType
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.obd.alerts.AlertEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    private val prefs = AlertPreferences()

    private fun snap(t: Long, coolant: Double = 90.0, volts: Double = 14.1, rpm: Double = 2000.0, mil: Boolean? = null,
                     src: DataSource = DataSource.ECU, mode: DataMode = DataMode.LIVE) = VehicleSnapshot(
        mode, ConnectionState.Connected("a", null),
        mapOf(
            VehicleMetric.COOLANT_TEMP to LiveValue(coolant, t, src),
            VehicleMetric.BATTERY_VOLTAGE to LiveValue(volts, t, src),
            VehicleMetric.RPM to LiveValue(rpm, t, src),
        ),
        null, t, mil,
    )

    private fun run(e: AlertEngine, from: Long, to: Long, step: Long = 500, p: AlertPreferences = prefs, f: (Long) -> VehicleSnapshot) =
        (from..to step step).flatMap { t -> e.onSnapshot(f(t), p, t) }

    @Test fun coolantNeedsToPersistThenFiresOnce() {
        val e = AlertEngine()
        assertTrue(run(e, 0, 4_000) { snap(it, coolant = 110.0) }.isEmpty())
        val fired = run(e, 4_500, 60_000) { snap(it, coolant = 110.0) }
        assertEquals(1, fired.size)
        assertEquals(AlertType.COOLANT_HIGH, fired.single().type)
    }

    @Test fun cooldownSuppressesFlappingValues() {
        val e = AlertEngine()
        val alerts = run(e, 0, 5 * 60_000, step = 1_000) { t -> snap(t, coolant = if ((t / 20_000) % 2 == 0L) 110.0 else 95.0) }
        assertEquals(1, alerts.count { it.type == AlertType.COOLANT_HIGH })
    }

    @Test fun firesAgainAfterCooldownWhenConditionReturns() {
        val e = AlertEngine()
        val p = prefs.copy(cooldownMinutes = 1)
        run(e, 0, 10_000, p = p) { snap(it, coolant = 110.0) }
        run(e, 10_500, 30_000, p = p) { snap(it, coolant = 95.0) }
        val second = run(e, 90_000, 100_000, p = p) { snap(it, coolant = 110.0) }
        assertEquals(1, second.size)
    }

    @Test fun lowBatteryOnlyWithEngineRunning() {
        val e = AlertEngine()
        assertTrue(run(e, 0, 60_000) { snap(it, volts = 12.4, rpm = 0.0) }.none { it.type == AlertType.BATTERY_LOW })
        assertEquals(1, run(e, 60_500, 120_000) { snap(it, volts = 12.4, rpm = 900.0) }.count { it.type == AlertType.BATTERY_LOW })
    }

    @Test fun demoAndDisabledNeverAlert() {
        assertTrue(run(AlertEngine(), 0, 60_000) { snap(it, coolant = 120.0, src = DataSource.SIMULATED, mode = DataMode.DEMO) }.isEmpty())
        val off = prefs.copy(enabled = prefs.enabled - AlertType.COOLANT_HIGH)
        assertTrue(run(AlertEngine(), 0, 60_000, p = off) { snap(it, coolant = 120.0) }.isEmpty())
    }

    @Test fun milTurningOnFiresCheckEngineOnce() {
        val e = AlertEngine()
        assertTrue(e.onSnapshot(snap(0, mil = false), prefs, 0).isEmpty())
        assertEquals(AlertType.CHECK_ENGINE, e.onSnapshot(snap(1_000, mil = true), prefs, 1_000).single().type)
        assertTrue(e.onSnapshot(snap(2_000, mil = true), prefs, 2_000).isEmpty())
    }

    @Test fun highRpmAndLinkLoss() {
        val e = AlertEngine()
        assertEquals(1, run(e, 0, 5_000) { snap(it, rpm = 6000.0) }.count { it.type == AlertType.HIGH_RPM })
        assertNotNull(e.onLinkLost(prefs, 0))
        assertNull(e.onLinkLost(prefs, 1_000))
    }
}
