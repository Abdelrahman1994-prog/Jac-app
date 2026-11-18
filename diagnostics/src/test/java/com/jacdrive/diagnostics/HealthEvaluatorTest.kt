package com.jacdrive.diagnostics

import com.jacdrive.core.model.VehicleMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthEvaluatorTest {
    @Test fun coolantBands() {
        assertEquals(HealthStatus.WARMING_UP, HealthEvaluator.classify(VehicleMetric.COOLANT_TEMP, 45.0, true))
        assertEquals(HealthStatus.NORMAL, HealthEvaluator.classify(VehicleMetric.COOLANT_TEMP, 92.0, true))
        assertEquals(HealthStatus.ATTENTION, HealthEvaluator.classify(VehicleMetric.COOLANT_TEMP, 106.0, true))
        assertEquals(HealthStatus.CRITICAL, HealthEvaluator.classify(VehicleMetric.COOLANT_TEMP, 115.0, true))
    }

    @Test fun batteryDependsOnEngineState() {
        assertEquals(HealthStatus.NORMAL, HealthEvaluator.classify(VehicleMetric.BATTERY_VOLTAGE, 12.6, false))
        assertEquals(HealthStatus.ATTENTION, HealthEvaluator.classify(VehicleMetric.BATTERY_VOLTAGE, 12.6, true))
        assertEquals(HealthStatus.NORMAL, HealthEvaluator.classify(VehicleMetric.BATTERY_VOLTAGE, 14.1, true))
    }
}
