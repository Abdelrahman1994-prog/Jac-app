package com.jacdrive.core.settings

import com.jacdrive.core.model.AppSettings
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.DashboardBackground
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.GaugeLayout
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val cockpit: Flow<CockpitPreferences>
    val alerts: Flow<com.jacdrive.core.model.AlertPreferences>

    suspend fun updateAlerts(transform: (com.jacdrive.core.model.AlertPreferences) -> com.jacdrive.core.model.AlertPreferences)

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setSpeedUnit(unit: SpeedUnit)
    suspend fun setTemperatureUnit(unit: TemperatureUnit)
    suspend fun setPollingInterval(ms: Long)
    suspend fun setDemoMode(enabled: Boolean)
    suspend fun setOnboardingDone()
    suspend fun setAdapter(address: String?, name: String?)
    suspend fun setAutoReconnect(enabled: Boolean)

    suspend fun setCockpitTheme(id: CockpitThemeId)
    suspend fun setAccentOverride(argb: Long?)
    suspend fun setGaugeLayout(layout: GaugeLayout)
    suspend fun setDashboardBackground(background: DashboardBackground)
    suspend fun setAnimationsEnabled(enabled: Boolean)
    suspend fun setWidgetVisible(widget: DashboardWidget, visible: Boolean)
    suspend fun setRedlineRpm(rpm: Int)
    suspend fun setKeepScreenOn(enabled: Boolean)
}
