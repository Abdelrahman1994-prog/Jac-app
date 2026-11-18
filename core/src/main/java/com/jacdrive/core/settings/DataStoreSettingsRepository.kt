package com.jacdrive.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val speedUnit = stringPreferencesKey("speed_unit")
        val tempUnit = stringPreferencesKey("temp_unit")
        val polling = longPreferencesKey("polling_ms")
        val demo = booleanPreferencesKey("demo_mode")
        val onboarding = booleanPreferencesKey("onboarding_done")

        val cockpitTheme = stringPreferencesKey("cockpit_theme")
        val accent = longPreferencesKey("cockpit_accent")
        val layout = stringPreferencesKey("cockpit_layout")
        val background = stringPreferencesKey("cockpit_background")
        val animations = booleanPreferencesKey("cockpit_animations")
        val hiddenWidgets = stringSetPreferencesKey("cockpit_hidden_widgets")
        val redline = intPreferencesKey("cockpit_redline")
        val keepScreenOn = booleanPreferencesKey("cockpit_keep_screen_on")
    }

    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val settings: Flow<AppSettings> = prefs.map { p ->
        val d = AppSettings()
        AppSettings(
            themeMode = enumOr(p[Keys.themeMode], d.themeMode),
            speedUnit = enumOr(p[Keys.speedUnit], d.speedUnit),
            temperatureUnit = enumOr(p[Keys.tempUnit], d.temperatureUnit),
            pollingIntervalMs = (p[Keys.polling] ?: d.pollingIntervalMs)
                .coerceIn(AppSettings.MIN_POLLING_MS, AppSettings.MAX_POLLING_MS),
            demoModeEnabled = p[Keys.demo] ?: d.demoModeEnabled,
            onboardingDone = p[Keys.onboarding] ?: d.onboardingDone,
        )
    }.distinctUntilChanged()

    override val cockpit: Flow<CockpitPreferences> = prefs.map { p ->
        val d = CockpitPreferences()
        val hidden = p[Keys.hiddenWidgets].orEmpty()
        CockpitPreferences(
            themeId = enumOr(p[Keys.cockpitTheme], d.themeId),
            accentOverride = p[Keys.accent],
            layout = enumOr(p[Keys.layout], d.layout),
            background = enumOr(p[Keys.background], d.background),
            animationsEnabled = p[Keys.animations] ?: d.animationsEnabled,
            visibleWidgets = DashboardWidget.entries.filterNot { it.name in hidden }.toSet(),
            redlineRpm = p[Keys.redline] ?: d.redlineRpm,
            keepScreenOn = p[Keys.keepScreenOn] ?: d.keepScreenOn,
        )
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) = put(Keys.themeMode, mode.name)
    override suspend fun setSpeedUnit(unit: SpeedUnit) = put(Keys.speedUnit, unit.name)
    override suspend fun setTemperatureUnit(unit: TemperatureUnit) = put(Keys.tempUnit, unit.name)
    override suspend fun setPollingInterval(ms: Long) =
        put(Keys.polling, ms.coerceIn(AppSettings.MIN_POLLING_MS, AppSettings.MAX_POLLING_MS))

    override suspend fun setDemoMode(enabled: Boolean) = put(Keys.demo, enabled)
    override suspend fun setOnboardingDone() = put(Keys.onboarding, true)

    override suspend fun setCockpitTheme(id: CockpitThemeId) = put(Keys.cockpitTheme, id.name)
    override suspend fun setAccentOverride(argb: Long?) {
        dataStore.edit { if (argb == null) it.remove(Keys.accent) else it[Keys.accent] = argb }
    }

    override suspend fun setGaugeLayout(layout: GaugeLayout) = put(Keys.layout, layout.name)
    override suspend fun setDashboardBackground(background: DashboardBackground) =
        put(Keys.background, background.name)

    override suspend fun setAnimationsEnabled(enabled: Boolean) = put(Keys.animations, enabled)
    override suspend fun setWidgetVisible(widget: DashboardWidget, visible: Boolean) {
        dataStore.edit { p ->
            val hidden = p[Keys.hiddenWidgets].orEmpty().toMutableSet()
            if (visible) hidden.remove(widget.name) else hidden.add(widget.name)
            p[Keys.hiddenWidgets] = hidden
        }
    }

    override suspend fun setRedlineRpm(rpm: Int) = put(Keys.redline, rpm.coerceIn(3000, 9000))
    override suspend fun setKeepScreenOn(enabled: Boolean) = put(Keys.keepScreenOn, enabled)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> enumOr(raw: String?, default: E): E =
        raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
