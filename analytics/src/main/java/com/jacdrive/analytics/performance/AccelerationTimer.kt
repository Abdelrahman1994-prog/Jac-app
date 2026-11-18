package com.jacdrive.analytics.performance

enum class RunType(val fromKmh: Double, val toKmh: Double) {
    ZERO_60(0.0, 60.0),
    ZERO_100(0.0, 100.0),
    SIXTY_120(60.0, 120.0),
}

enum class TimerState { WAITING_FOR_STOP, READY, RUNNING, FINISHED, ABORTED }

/**
 * Times one acceleration run from ECU vehicle-speed samples. Crossing times are linearly
 * interpolated between samples, so accuracy is limited mainly by the speed PID's 1 km/h
 * resolution and the polling rate (typically ±0.1–0.2 s).
 *
 * 0–x runs need a standstill first; the launch is the moment speed passes [LAUNCH_KMH].
 * 60–120 starts when speed crosses 60 upwards. A run aborts if speed drops back, or after
 * [TIMEOUT_MS].
 */
class AccelerationTimer(val type: RunType) {
    var state: TimerState = if (type.fromKmh == 0.0) TimerState.WAITING_FOR_STOP else TimerState.READY
        private set
    var resultS: Double? = null
        private set
    var startMs: Double = 0.0
        private set

    private var lastT = -1L
    private var lastV = 0.0

    fun reset() {
        state = if (type.fromKmh == 0.0) TimerState.WAITING_FOR_STOP else TimerState.READY
        resultS = null
        lastT = -1
    }

    /** Feed each new speed sample (ECU timestamp). Returns true when the run just finished. */
    fun onSpeed(kmh: Double, tMs: Long): Boolean {
        if (tMs <= lastT) return false
        val prevT = lastT
        val prevV = lastV
        lastT = tMs
        lastV = kmh
        if (prevT < 0) {
            if (state == TimerState.WAITING_FOR_STOP && kmh == 0.0) state = TimerState.READY
            return false
        }
        when (state) {
            TimerState.WAITING_FOR_STOP -> if (kmh == 0.0) state = TimerState.READY
            TimerState.READY -> {
                val startAt = if (type.fromKmh == 0.0) LAUNCH_KMH else type.fromKmh
                if (type.fromKmh == 0.0 && kmh == 0.0) return false
                if (prevV < startAt && kmh >= startAt) {
                    startMs = interpolate(prevT, prevV, tMs, kmh, startAt)
                    state = TimerState.RUNNING
                    return finishIfReached(prevT, prevV, tMs, kmh)
                }
                // 0–x: moving without having launched from a stop → wait for the next stop.
                if (type.fromKmh == 0.0 && prevV > LAUNCH_KMH) state = TimerState.WAITING_FOR_STOP
            }
            TimerState.RUNNING -> {
                if (kmh < prevV - 3 || kmh < type.fromKmh - 5 || tMs - startMs > TIMEOUT_MS) {
                    state = TimerState.ABORTED
                    return false
                }
                return finishIfReached(prevT, prevV, tMs, kmh)
            }
            TimerState.FINISHED, TimerState.ABORTED -> Unit
        }
        return false
    }

    private fun finishIfReached(t0: Long, v0: Double, t1: Long, v1: Double): Boolean {
        if (v1 < type.toKmh) return false
        val end = interpolate(t0, v0, t1, v1, type.toKmh)
        resultS = (end - startMs) / 1000.0
        state = TimerState.FINISHED
        return true
    }

    private fun interpolate(t0: Long, v0: Double, t1: Long, v1: Double, target: Double): Double =
        if (v1 == v0) t1.toDouble() else t0 + (target - v0) / (v1 - v0) * (t1 - t0)

    companion object {
        const val LAUNCH_KMH = 0.5
        const val TIMEOUT_MS = 60_000L
    }
}
