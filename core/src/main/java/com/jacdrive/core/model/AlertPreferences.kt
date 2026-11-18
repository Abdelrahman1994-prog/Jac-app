package com.jacdrive.core.model

enum class AlertType { COOLANT_HIGH, BATTERY_LOW, BATTERY_HIGH, HIGH_RPM, CHECK_ENGINE, OBD_DISCONNECTED, MAINTENANCE_DUE }

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class VoiceLanguage { APP, ARABIC, ENGLISH }

/** User-configurable alert behaviour. Defaults are conservative generic values, not JAC specifications. */
data class AlertPreferences(
    val voiceEnabled: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.APP,
    val notificationsEnabled: Boolean = true,
    val enabled: Set<AlertType> = AlertType.entries.toSet(),
    val coolantThresholdC: Int = 105,
    val batteryLowRunningV: Double = 13.0,
    val batteryHighV: Double = 15.2,
    val rpmThreshold: Int = 5500,
    /** Minimum time before the same alert can fire again. */
    val cooldownMinutes: Int = 10,
) {
    fun isEnabled(type: AlertType) = type in enabled
}
