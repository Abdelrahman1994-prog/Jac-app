package com.jacdrive.obd

import com.jacdrive.obd.demo.DemoDriveModel
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDriveModelTest {
    @Test fun valuesStayWithinPlausibleRanges() {
        val m = DemoDriveModel()
        var maxSpeed = 0.0
        repeat(20_000) {
            val s = m.step(0.1)
            maxSpeed = maxOf(maxSpeed, s.speedKmh)
            assertTrue(s.speedKmh in 0.0..200.0)
            assertTrue(s.rpm in 600.0..7000.0)
            assertTrue(s.engineLoadPct in 0.0..100.0)
            assertTrue(s.throttlePct in 0.0..100.0)
            assertTrue(s.coolantC in 0.0..100.0)
        }
        assertTrue("simulation should actually drive", maxSpeed > 40)
    }
}
