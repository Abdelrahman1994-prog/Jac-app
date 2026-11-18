package com.jacdrive.dashboard

import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.dashboard.cockpit.CockpitWarnings
import com.jacdrive.dashboard.cockpit.Severity
import com.jacdrive.dashboard.cockpit.Telltale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitWarningsTest {
    private fun snap(vararg v: Pair<VehicleMetric, Double>, ts: Long = 1_000) = VehicleSnapshot(
        mode = DataMode.LIVE,
        connection = ConnectionState.Connected("test", null),
        values = v.associate { it.first to LiveValue(it.second, ts, DataSource.ECU) },
        supportedMetrics = null,
        timestampMs = ts,
    )

    @Test fun hotCoolantIsCritical() {
        val w = CockpitWarnings.evaluate(snap(VehicleMetric.COOLANT_TEMP to 115.0), 6000, 1_500)
        assertEquals(Severity.CRITICAL, w[Telltale.COOLANT_HOT])
    }

    @Test fun normalValuesLightNothing() {
        val w = CockpitWarnings.evaluate(
            snap(VehicleMetric.COOLANT_TEMP to 90.0, VehicleMetric.BATTERY_VOLTAGE to 14.1, VehicleMetric.RPM to 2500.0),
            6000, 1_500,
        )
        assertTrue(w.isEmpty())
    }

    @Test fun lowVoltageOnlyWarnsWhenEngineRunning() {
        assertTrue(CockpitWarnings.evaluate(snap(VehicleMetric.BATTERY_VOLTAGE to 12.4), 6000, 1_500).isEmpty())
        val running = CockpitWarnings.evaluate(snap(VehicleMetric.BATTERY_VOLTAGE to 12.4, VehicleMetric.RPM to 900.0), 6000, 1_500)
        assertEquals(Severity.WARNING, running[Telltale.BATTERY_VOLTAGE])
    }

    @Test fun staleValuesAreIgnored() {
        val w = CockpitWarnings.evaluate(snap(VehicleMetric.COOLANT_TEMP to 120.0, ts = 0), 6000, 60_000)
        assertTrue(w.isEmpty())
    }

    @Test fun overRev() {
        val w = CockpitWarnings.evaluate(snap(VehicleMetric.RPM to 6200.0), 6000, 1_500)
        assertEquals(Severity.CRITICAL, w[Telltale.OVER_REV])
    }
}
