package com.jacdrive.diagnostics

import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness

enum class HealthStatus { NORMAL, ATTENTION, CRITICAL, WARMING_UP, UNKNOWN }

data class HealthItem(val metric: VehicleMetric, val value: Double?, val status: HealthStatus)

/**
 * Classifies individual live values. Only fresh values are judged; anything else is UNKNOWN.
 * Ranges are generic petrol-engine guidelines, not JAC-specific specifications.
 */
object HealthEvaluator {
    private const val RUNNING_RPM = 400.0

    val monitored = listOf(
        VehicleMetric.COOLANT_TEMP,
        VehicleMetric.BATTERY_VOLTAGE,
        VehicleMetric.ENGINE_LOAD,
        VehicleMetric.INTAKE_AIR_TEMP,
        VehicleMetric.INTAKE_MANIFOLD_PRESSURE,
        VehicleMetric.FUEL_LEVEL,
    )

    fun evaluate(snapshot: VehicleSnapshot, nowMs: Long): List<HealthItem> {
        fun fresh(m: VehicleMetric) = snapshot[m]?.takeIf { it.freshness(m, nowMs) == Freshness.LIVE }?.value
        val running = (fresh(VehicleMetric.RPM) ?: 0.0) > RUNNING_RPM
        return monitored.map { m ->
            val v = fresh(m)
            HealthItem(m, v, if (v == null) HealthStatus.UNKNOWN else classify(m, v, running))
        }
    }

    fun classify(metric: VehicleMetric, v: Double, engineRunning: Boolean): HealthStatus = when (metric) {
        VehicleMetric.COOLANT_TEMP -> when {
            v >= 112 -> HealthStatus.CRITICAL
            v >= 105 -> HealthStatus.ATTENTION
            v < 70 -> HealthStatus.WARMING_UP
            else -> HealthStatus.NORMAL
        }
        VehicleMetric.BATTERY_VOLTAGE -> when {
            v < 11.8 || v > 16.0 -> HealthStatus.CRITICAL
            engineRunning && (v < 13.0 || v > 15.2) -> HealthStatus.ATTENTION
            !engineRunning && v < 12.2 -> HealthStatus.ATTENTION
            else -> HealthStatus.NORMAL
        }
        VehicleMetric.INTAKE_AIR_TEMP -> if (v > 70) HealthStatus.ATTENTION else HealthStatus.NORMAL
        VehicleMetric.FUEL_LEVEL -> if (v < 10) HealthStatus.ATTENTION else HealthStatus.NORMAL
        else -> HealthStatus.NORMAL
    }
}
