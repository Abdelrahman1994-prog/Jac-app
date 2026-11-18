package com.jacdrive.diagnostics.data

import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness
import kotlin.math.min

enum class DeductionReason { MIL_ON, STORED_CODES, PENDING_CODES, PERMANENT_CODES, READINESS_INCOMPLETE, COOLANT_HIGH, BATTERY_ABNORMAL, CRITICAL_CODE }

data class Deduction(val reason: DeductionReason, val points: Int, val detail: String? = null)

data class HealthScoreResult(
    val score: Int,
    val deductions: List<Deduction>,
    val scanTimestampMs: Long,
    /** Whether live sensor checks were included (only with fresh real data). */
    val liveIncluded: Boolean,
)

/**
 * Transparent vehicle health score.
 * Starts at 100. Only data that was actually reported is used; missing items are excluded,
 * never assumed healthy or faulty. Requires a completed fault scan.
 */
object HealthScore {
    const val MIL = 30
    const val PER_STORED = 10; const val MAX_STORED = 40
    const val PER_PENDING = 5; const val MAX_PENDING = 15
    const val PER_PERMANENT = 5; const val MAX_PERMANENT = 10
    const val PER_MONITOR = 3; const val MAX_MONITORS = 12
    const val CRITICAL_CODE = 20
    const val COOLANT = 15
    const val BATTERY = 10

    fun compute(scan: DiagnosticSession?, snapshot: VehicleSnapshot, nowMs: Long, criticalCodes: Set<String> = emptySet()): HealthScoreResult? {
        if (scan == null || scan.kind != SessionKind.SCAN || !scan.complete) return null
        val d = mutableListOf<Deduction>()
        if (scan.milOn == true) d += Deduction(DeductionReason.MIL_ON, MIL)
        if (scan.stored.isNotEmpty()) d += Deduction(DeductionReason.STORED_CODES, min(MAX_STORED, scan.stored.size * PER_STORED), scan.stored.joinToString())
        if (scan.pending.isNotEmpty()) d += Deduction(DeductionReason.PENDING_CODES, min(MAX_PENDING, scan.pending.size * PER_PENDING), scan.pending.joinToString())
        scan.permanent?.takeIf { it.isNotEmpty() }?.let { d += Deduction(DeductionReason.PERMANENT_CODES, min(MAX_PERMANENT, it.size * PER_PERMANENT), it.joinToString()) }
        val critical = scan.allCodes.filter { it in criticalCodes }
        if (critical.isNotEmpty()) d += Deduction(DeductionReason.CRITICAL_CODE, CRITICAL_CODE, critical.joinToString())
        scan.readiness?.filterValues { it == com.jacdrive.obd.dtc.MonitorState.INCOMPLETE }?.keys?.takeIf { it.isNotEmpty() }?.let {
            d += Deduction(DeductionReason.READINESS_INCOMPLETE, min(MAX_MONITORS, it.size * PER_MONITOR), it.joinToString { m -> m.name })
        }

        fun fresh(m: VehicleMetric) = snapshot[m]?.takeIf { it.freshness(m, nowMs) == Freshness.LIVE && it.source != com.jacdrive.core.model.DataSource.SIMULATED }?.value
        val coolant = fresh(VehicleMetric.COOLANT_TEMP)
        val battery = fresh(VehicleMetric.BATTERY_VOLTAGE)
        val rpm = fresh(VehicleMetric.RPM)
        if (coolant != null && coolant > 105) d += Deduction(DeductionReason.COOLANT_HIGH, COOLANT, "%.0f °C".format(coolant))
        if (battery != null) {
            val running = (rpm ?: 0.0) > 400
            val bad = battery < 11.8 || battery > 15.5 || (running && battery < 13.0) || (!running && battery < 12.2)
            if (bad) d += Deduction(DeductionReason.BATTERY_ABNORMAL, BATTERY, "%.1f V".format(battery))
        }
        return HealthScoreResult(
            score = (100 - d.sumOf { it.points }).coerceIn(0, 100),
            deductions = d,
            scanTimestampMs = scan.timestampMs,
            liveIncluded = coolant != null || battery != null,
        )
    }
}
