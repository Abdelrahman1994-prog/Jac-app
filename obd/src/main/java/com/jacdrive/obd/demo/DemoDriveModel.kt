package com.jacdrive.obd.demo

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * A deterministic, physically plausible drive-cycle generator used ONLY for demo mode.
 * Values produced here are never stored as real trip/diagnostic data and are always tagged
 * as simulated by the caller.
 */
class DemoDriveModel(seed: Int = 7) {
    private val random = Random(seed)

    enum class Phase { IDLE, ACCELERATE, CRUISE, DECELERATE }

    data class State(
        val phase: Phase = Phase.IDLE,
        val phaseElapsedS: Double = 0.0,
        val phaseDurationS: Double = 5.0,
        val targetSpeedKmh: Double = 0.0,
        val speedKmh: Double = 0.0,
        val throttlePct: Double = 0.0,
        val rpm: Double = IDLE_RPM,
        val gear: Int = 0,
        val engineLoadPct: Double = 20.0,
        val coolantC: Double = 38.0,
        val intakeC: Double = 31.0,
        val batteryV: Double = 14.1,
        val fuelLevelPct: Double = 68.0,
        val mapKpa: Double = 32.0,
        val timingAdvanceDeg: Double = 12.0,
        val fuelRateLph: Double = 0.9,
    )

    var state = State()
        private set

    fun step(dtSeconds: Double): State {
        val s = state
        val dt = dtSeconds.coerceIn(0.01, 1.0)
        var phase = s.phase
        var elapsed = s.phaseElapsedS + dt
        var duration = s.phaseDurationS
        var target = s.targetSpeedKmh

        // Phase transitions.
        val reachedTarget = when (phase) {
            Phase.ACCELERATE -> s.speedKmh >= target - 1
            Phase.DECELERATE -> s.speedKmh <= target + 1
            else -> elapsed >= duration
        }
        if (reachedTarget) {
            elapsed = 0.0
            when (phase) {
                Phase.IDLE -> {
                    phase = Phase.ACCELERATE
                    target = listOf(50.0, 70.0, 90.0, 110.0, 125.0).random(random)
                }
                Phase.ACCELERATE -> {
                    phase = Phase.CRUISE
                    duration = 12.0 + random.nextDouble() * 14.0
                }
                Phase.CRUISE -> {
                    phase = Phase.DECELERATE
                    target = if (random.nextDouble() < 0.4) 0.0 else max(30.0, target - 40 - random.nextDouble() * 30)
                }
                Phase.DECELERATE -> {
                    if (target <= 0.5) {
                        phase = Phase.IDLE
                        duration = 4.0 + random.nextDouble() * 4.0
                    } else {
                        phase = Phase.ACCELERATE
                        target = min(130.0, target + 30 + random.nextDouble() * 40)
                    }
                }
            }
        }

        // Driver input.
        val throttleTarget = when (phase) {
            Phase.IDLE -> 0.0
            Phase.ACCELERATE -> if (target > 100) 62.0 else 42.0
            Phase.CRUISE -> 14.0 + s.speedKmh * 0.09 + (random.nextDouble() - 0.5) * 4
            Phase.DECELERATE -> 0.0
        }
        val throttle = s.throttlePct + (throttleTarget - s.throttlePct) * min(1.0, dt * 3)

        // Longitudinal dynamics (m/s^2).
        val accel = when (phase) {
            Phase.ACCELERATE -> throttle / 100.0 * 4.2 * (1 - s.speedKmh / 210.0)
            Phase.CRUISE -> (target - s.speedKmh) * 0.15
            Phase.DECELERATE -> -2.2 - (if (target <= 0.5 && s.speedKmh < 25) 0.6 else 0.0)
            Phase.IDLE -> -3.0
        }
        val speed = (s.speedKmh + accel * 3.6 * dt).coerceIn(0.0, 200.0)

        // Simple 6-speed gearbox model for RPM.
        val gear = when {
            speed < 1 -> 0
            speed < 18 -> 1
            speed < 36 -> 2
            speed < 58 -> 3
            speed < 80 -> 4
            speed < 102 -> 5
            else -> 6
        }
        val rpmPerKmh = doubleArrayOf(0.0, 105.0, 62.0, 43.0, 33.0, 27.0, 22.5)[gear]
        val rawRpm = if (gear == 0) IDLE_RPM else speed * rpmPerKmh + throttle * 9
        val rpmTarget = max(IDLE_RPM, rawRpm) + (random.nextDouble() - 0.5) * 30
        val rpm = s.rpm + (rpmTarget - s.rpm) * min(1.0, dt * 6)

        val load = (18 + throttle * 0.75 + max(0.0, accel) * 6).coerceIn(12.0, 98.0)
        val fuelRate = 0.75 + load / 100.0 * rpm / 1000.0 * 2.6
        val coolant = if (s.coolantC < 89.5) s.coolantC + dt * 0.35 else 89.5 + (random.nextDouble() - 0.5) * 1.2
        val intake = 30.0 + 3 * (1 - speed / 130.0).coerceIn(0.0, 1.0) + (random.nextDouble() - 0.5) * 0.4
        val battery = 14.15 + (random.nextDouble() - 0.5) * 0.12
        val fuelLevel = max(5.0, s.fuelLevelPct - fuelRate * dt / 3600.0 / DEMO_TANK_LITRES * 100)
        val map = (28 + load * 0.72).coerceAtMost(101.0)
        val advance = (8 + rpm / 400.0 - load * 0.05).coerceIn(2.0, 35.0)

        state = State(
            phase = phase,
            phaseElapsedS = elapsed,
            phaseDurationS = duration,
            targetSpeedKmh = target,
            speedKmh = speed,
            throttlePct = throttle,
            rpm = rpm,
            gear = gear,
            engineLoadPct = load,
            coolantC = coolant,
            intakeC = intake,
            batteryV = battery,
            fuelLevelPct = fuelLevel,
            mapKpa = map,
            timingAdvanceDeg = advance,
            fuelRateLph = fuelRate,
        )
        return state
    }

    companion object {
        const val IDLE_RPM = 780.0
        /** Only used to make the simulated fuel gauge move. Not a JAC J7 specification. */
        private const val DEMO_TANK_LITRES = 55.0
    }
}
