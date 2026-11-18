package com.jacdrive.analytics.trips

import com.jacdrive.core.database.TripEntity
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness

/**
 * Turns the live snapshot stream into trips. Pure logic, no Android.
 *
 * - Only LIVE snapshots with fresh ECU values are used; demo data never creates a trip.
 * - A trip starts when the engine runs (RPM > 300) and ends after [idleTimeoutMs] without a
 *   running engine (engine off, or the adapter disconnected). Brief link drops continue the trip.
 * - Distance is integrated from ECU speed (trapezoidal); gaps > [maxGapMs] are not integrated.
 * - Trips shorter than [minDistanceKm] and [minDurationS] are discarded.
 */
class TripTracker(
    private val idleTimeoutMs: Long = 90_000,
    private val maxGapMs: Long = 5_000,
    private val minDistanceKm: Double = 0.2,
    private val minDurationS: Long = 60,
) {
    sealed interface Event {
        data class Started(val trip: TripEntity) : Event
        data class Checkpoint(val trip: TripEntity) : Event
        data class Finished(val trip: TripEntity) : Event
        data object Discarded : Event
    }

    private var active = false
    private var startMs = 0L
    private var lastRunningMs = 0L
    private var lastSampleMs = 0L
    private var lastSpeed: Double? = null
    private var lastRate: Double? = null
    private var distanceKm = 0.0
    private var runningMs = 0L
    private var movingMs = 0L
    private var maxSpeed = 0.0
    private var rpmWeighted = 0.0
    private var rpmMs = 0L
    private var fuelL: Double? = null
    private var fuelSource: String? = null
    private var lastCheckpointMs = 0L

    val isActive: Boolean get() = active

    fun onSnapshot(s: VehicleSnapshot, nowMs: Long): Event? {
        val live = s.mode == DataMode.LIVE
        fun ecu(m: VehicleMetric) = s[m]?.takeIf { live && it.source != DataSource.SIMULATED && it.freshness(m, nowMs) == Freshness.LIVE }
        val rpm = ecu(VehicleMetric.RPM)?.value
        val speedValue = ecu(VehicleMetric.SPEED)
        val rate = ecu(VehicleMetric.FUEL_RATE)
        val running = rpm != null && rpm > RUNNING_RPM

        if (!active) {
            if (!running) return null
            reset(nowMs)
            active = true
            return Event.Started(snapshotTrip(nowMs, completed = false))
        }

        if (running) lastRunningMs = nowMs
        val ts = speedValue?.timestampMs
        if (speedValue != null && ts != null && ts > lastSampleMs) {
            val dt = ts - lastSampleMs
            val v = speedValue.value
            val prev = lastSpeed
            if (lastSampleMs > 0 && dt in 1..maxGapMs) {
                val h = dt / 3_600_000.0
                if (prev != null) distanceKm += (prev + v) / 2 * h
                if (running) runningMs += dt
                if (v > 0.5 || (prev ?: 0.0) > 0.5) movingMs += dt
                if (rpm != null) { rpmWeighted += rpm * dt; rpmMs += dt }
                val r = rate?.value
                val pr = lastRate
                if (r != null && pr != null) {
                    fuelL = (fuelL ?: 0.0) + (r + pr) / 2 * h
                    fuelSource = if (rate.source == DataSource.ECU) "ECU_FUEL_RATE" else "MAF_ESTIMATE"
                }
            }
            maxSpeed = maxOf(maxSpeed, v)
            lastSpeed = v
            lastRate = rate?.value
            lastSampleMs = ts
        }

        if (nowMs - lastRunningMs > idleTimeoutMs) {
            active = false
            val trip = snapshotTrip(lastRunningMs, completed = true)
            return if (trip.distanceKm < minDistanceKm && trip.durationS < minDurationS) Event.Discarded else Event.Finished(trip)
        }
        if (nowMs - lastCheckpointMs >= CHECKPOINT_MS) {
            lastCheckpointMs = nowMs
            return Event.Checkpoint(snapshotTrip(nowMs, completed = false))
        }
        return null
    }

    private fun reset(nowMs: Long) {
        startMs = nowMs; lastRunningMs = nowMs; lastSampleMs = 0; lastSpeed = null; lastRate = null
        distanceKm = 0.0; runningMs = 0; movingMs = 0; maxSpeed = 0.0; rpmWeighted = 0.0; rpmMs = 0
        fuelL = null; fuelSource = null; lastCheckpointMs = nowMs
    }

    private fun snapshotTrip(endMs: Long, completed: Boolean) = TripEntity(
        startMs = startMs,
        endMs = endMs,
        distanceKm = distanceKm,
        durationS = runningMs / 1000,
        movingS = movingMs / 1000,
        maxSpeedKmh = maxSpeed,
        avgRpm = if (rpmMs > 0) rpmWeighted / rpmMs else null,
        fuelUsedL = fuelL,
        fuelSource = fuelSource,
        completed = completed,
    )

    companion object {
        const val RUNNING_RPM = 300.0
        const val CHECKPOINT_MS = 30_000L
    }
}
