package com.jacdrive.core.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** Delay between OBD polling cycles. Bounded to keep adapters and ECUs comfortable. */
    val pollingIntervalMs: Long = DEFAULT_POLLING_MS,
    val demoModeEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val MIN_POLLING_MS = 100L
        const val MAX_POLLING_MS = 2_000L
        const val DEFAULT_POLLING_MS = 250L
    }
}
