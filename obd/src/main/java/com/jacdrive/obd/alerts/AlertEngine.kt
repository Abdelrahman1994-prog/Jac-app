package com.jacdrive.obd.alerts

import com.jacdrive.core.model.AlertPreferences
import com.jacdrive.core.model.AlertSeverity
import com.jacdrive.core.model.AlertType
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness

data class Alert(val type: AlertType, val severity: AlertSeverity, val value: Double? = null, val detail: String? = null)

/**
 * Decides when to alert. Pure logic.
 *
 * Avoids alert fatigue by design:
 * - a condition must persist for a sustain time before it fires (no alerts on a single noisy sample);
 * - it must clear past a hysteresis margin before it can fire again;
 * - the same alert never fires twice within the cooldown;
 * - only fresh, real vehicle data counts — demo data never alerts.
 */
class AlertEngine {
    private class Track(var since: Long? = null, var active: Boolean = false, var lastFired: Long = Long.MIN_VALUE / 2)

    private val tracks = AlertType.entries.associateWith { Track() }
    private var lastMil: Boolean? = null

    fun onSnapshot(s: VehicleSnapshot, prefs: AlertPreferences, nowMs: Long): List<Alert> {
        if (s.mode != DataMode.LIVE) return emptyList()
        fun real(m: VehicleMetric) = s[m]?.takeIf { it.source != DataSource.SIMULATED && it.freshness(m, nowMs) == Freshness.LIVE }?.value
        val out = mutableListOf<Alert>()
        val rpm = real(VehicleMetric.RPM)
        val running = rpm != null && rpm > 400

        real(VehicleMetric.COOLANT_TEMP)?.let { t ->
            check(AlertType.COOLANT_HIGH, t >= prefs.coolantThresholdC, t < prefs.coolantThresholdC - 3, 5_000, prefs, nowMs)
                ?.let { out += Alert(it, AlertSeverity.CRITICAL, t) }
        }
        real(VehicleMetric.BATTERY_VOLTAGE)?.let { v ->
            check(AlertType.BATTERY_LOW, running && v < prefs.batteryLowRunningV, !running || v > prefs.batteryLowRunningV + 0.3, 30_000, prefs, nowMs)
                ?.let { out += Alert(it, AlertSeverity.WARNING, v) }
            check(AlertType.BATTERY_HIGH, v > prefs.batteryHighV, v < prefs.batteryHighV - 0.3, 10_000, prefs, nowMs)
                ?.let { out += Alert(it, AlertSeverity.WARNING, v) }
        }
        if (rpm != null) {
            check(AlertType.HIGH_RPM, rpm >= prefs.rpmThreshold, rpm < prefs.rpmThreshold - 300, 1_500, prefs, nowMs)
                ?.let { out += Alert(it, AlertSeverity.WARNING, rpm) }
        }
        s.milOn?.let { mil ->
            if (mil && lastMil != true && prefs.isEnabled(AlertType.CHECK_ENGINE)) {
                val t = tracks.getValue(AlertType.CHECK_ENGINE)
                if (nowMs - t.lastFired >= prefs.cooldownMinutes * 60_000L) {
                    t.lastFired = nowMs
                    out += Alert(AlertType.CHECK_ENGINE, AlertSeverity.CRITICAL)
                }
            }
            lastMil = mil
        }
        return out
    }

    /** The adapter link dropped without the user asking for it. */
    fun onLinkLost(prefs: AlertPreferences, nowMs: Long): Alert? {
        if (!prefs.isEnabled(AlertType.OBD_DISCONNECTED)) return null
        val t = tracks.getValue(AlertType.OBD_DISCONNECTED)
        if (nowMs - t.lastFired < prefs.cooldownMinutes * 60_000L) return null
        t.lastFired = nowMs
        lastMil = null
        return Alert(AlertType.OBD_DISCONNECTED, AlertSeverity.WARNING)
    }

    private fun check(type: AlertType, condition: Boolean, cleared: Boolean, sustainMs: Long, prefs: AlertPreferences, now: Long): AlertType? {
        val t = tracks.getValue(type)
        if (!condition) {
            t.since = null
            if (cleared) t.active = false
            return null
        }
        if (t.active || !prefs.isEnabled(type)) return null
        val since = t.since ?: now.also { t.since = it }
        if (now - since < sustainMs) return null
        t.active = true
        if (now - t.lastFired < prefs.cooldownMinutes * 60_000L) return null
        t.lastFired = now
        return type
    }
}
