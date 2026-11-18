package com.jacdrive.obd

import com.jacdrive.obd.calc.TripAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripAccumulatorTest {
    @Test fun constantSpeedForOneHour() {
        val t = TripAccumulator(maxGapMs = 2_000)
        var ts = 0L
        repeat(3601) { t.add(60.0, ts, fuelRateLph = 6.0); ts += 1_000 }
        assertEquals(60.0, t.distanceKm, 0.001)
        assertEquals(6.0, t.fuelUsedLitres, 0.001)
        assertEquals(10.0, t.averageConsumption()!!, 0.001)
    }

    @Test fun gapsAreNotIntegrated() {
        val t = TripAccumulator(maxGapMs = 2_000)
        t.add(100.0, 0)
        t.add(100.0, 60_000)
        assertEquals(0.0, t.distanceKm, 0.0)
    }

    @Test fun noConsumptionWithoutFuelData() {
        val t = TripAccumulator()
        var ts = 0L
        repeat(100) { t.add(80.0, ts); ts += 1_000 }
        assertNull(t.averageConsumption())
    }
}
