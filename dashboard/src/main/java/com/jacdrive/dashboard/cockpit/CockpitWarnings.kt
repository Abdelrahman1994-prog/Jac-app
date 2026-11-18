package com.jacdrive.dashboard.cockpit

import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness

enum class Telltale { COOLANT_HOT, BATTERY_VOLTAGE, OVER_REV }

enum class Severity { WARNING, CRITICAL }

/**
 * Evaluates the warning lamps shown on the cockpit. Only fresh values are considered so a stale
 * reading can never light (or extinguish) a lamp. Thresholds are conservative generic values;
 * they become user-configurable with the alert engine.
 */
object CockpitWarnings {
    const val COOLANT_WARNING_C = 105.0
    const val COOLANT_CRITICAL_C = 112.0
    const val BATTERY_LOW_RUNNING_V = 12.8
    const val BATTERY_CRITICAL_V = 11.8
    const val BATTERY_HIGH_V = 15.2
    private const val ENGINE_RUNNING_RPM = 400.0

    fun evaluate(snapshot: VehicleSnapshot, redlineRpm: Int, nowMs: Long): Map<Telltale, Severity> {
        fun fresh(m: VehicleMetric): Double? =
            snapshot[m]?.takeIf { it.freshness(m, nowMs) == Freshness.LIVE }?.value

        val result = mutableMapOf<Telltale, Severity>()
        fresh(VehicleMetric.COOLANT_TEMP)?.let { t ->
            when {
                t >= COOLANT_CRITICAL_C -> result[Telltale.COOLANT_HOT] = Severity.CRITICAL
                t >= COOLANT_WARNING_C -> result[Telltale.COOLANT_HOT] = Severity.WARNING
            }
        }
        val rpm = fresh(VehicleMetric.RPM)
        fresh(VehicleMetric.BATTERY_VOLTAGE)?.let { v ->
            val running = rpm != null && rpm > ENGINE_RUNNING_RPM
            when {
                v < BATTERY_CRITICAL_V || v > BATTERY_HIGH_V + 0.8 -> result[Telltale.BATTERY_VOLTAGE] = Severity.CRITICAL
                v > BATTERY_HIGH_V -> result[Telltale.BATTERY_VOLTAGE] = Severity.WARNING
                running && v < BATTERY_LOW_RUNNING_V -> result[Telltale.BATTERY_VOLTAGE] = Severity.WARNING
            }
        }
        if (rpm != null && rpm >= redlineRpm) result[Telltale.OVER_REV] = Severity.CRITICAL
        return result
    }
}
