package com.jacdrive.analytics.performance

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import kotlin.math.sqrt

/** Horizontal acceleration in g. Longitudinal > 0 = accelerating, lateral > 0 = to the right. */
data class GSample(val longitudinal: Double, val lateral: Double, val tMs: Long) {
    val magnitude: Double get() = sqrt(longitudinal * longitudinal + lateral * lateral)
}

object GMath {
    private const val G = 9.80665

    /**
     * Projects linear acceleration onto the road plane using the gravity vector.
     * Assumes the phone is mounted roughly upright with its screen facing the driver, so the
     * vehicle's forward direction is the phone's −Z axis projected onto the horizontal plane.
     * Returns null when the phone lies too flat for that assumption to hold.
     */
    fun project(linear: FloatArray, gravity: FloatArray, tMs: Long): GSample? {
        val gn = norm(gravity).takeIf { it > 1e-3 } ?: return null
        val up = DoubleArray(3) { gravity[it] / gn }
        // Forward = −Z minus its vertical component.
        val z = doubleArrayOf(0.0, 0.0, -1.0)
        val zDotUp = dot(z, up)
        val fwd = DoubleArray(3) { z[it] - zDotUp * up[it] }
        val fn = sqrt(dot(fwd, fwd))
        if (fn < 0.35) return null // phone lying flat: forward axis undefined
        for (i in 0..2) fwd[i] /= fn
        val right = cross(fwd, up)
        val a = DoubleArray(3) { linear[it].toDouble() }
        return GSample(dot(a, fwd) / G, dot(a, right) / G, tMs)
    }

    private fun norm(v: FloatArray) = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0],
    )
}

/** Phone-sensor G-force stream (Android linear-acceleration + gravity sensors). */
class GForceSensor @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager get() = context.getSystemService(SensorManager::class.java)

    val available: Boolean
        get() = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null && manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null

    /** Emits samples, or null while the phone is too flat to tell forward from sideways. */
    fun samples(): Flow<GSample?> = callbackFlow {
        val sm = manager ?: run { close(); return@callbackFlow }
        val gravity = FloatArray(3)
        var hasGravity = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_GRAVITY -> { e.values.copyInto(gravity, 0, 0, 3); hasGravity = true }
                    Sensor.TYPE_LINEAR_ACCELERATION -> if (hasGravity) trySend(GMath.project(e.values, gravity, System.currentTimeMillis()))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        awaitClose { sm.unregisterListener(listener) }
    }
}
