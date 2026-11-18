package com.jacdrive.core

import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.freshness
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {
    @Test fun kmhToMph() = assertEquals(62.137, UnitConverter.speed(100.0, SpeedUnit.MPH), 0.001)
    @Test fun kmhStaysKmh() = assertEquals(100.0, UnitConverter.speed(100.0, SpeedUnit.KMH), 0.0)
    @Test fun celsiusToFahrenheit() =
        assertEquals(194.0, UnitConverter.temperature(90.0, TemperatureUnit.FAHRENHEIT), 0.001)

    @Test fun freshnessStates() {
        val v = LiveValue(10.0, timestampMs = 1_000, source = DataSource.ECU)
        assertEquals(Freshness.LIVE, v.freshness(VehicleMetric.SPEED, 2_000))
        assertEquals(Freshness.STALE, v.freshness(VehicleMetric.SPEED, 10_000))
        assertEquals(Freshness.MISSING, null.freshness(VehicleMetric.SPEED, 10_000))
    }
}
