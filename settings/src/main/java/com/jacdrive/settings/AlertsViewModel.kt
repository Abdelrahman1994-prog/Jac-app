package com.jacdrive.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.database.AlertDao
import com.jacdrive.core.database.AlertEventEntity
import com.jacdrive.core.model.AlertPreferences
import com.jacdrive.core.model.AlertType
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.alerts.Alert
import com.jacdrive.obd.alerts.AlertCenter
import com.jacdrive.obd.alerts.VoiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val center: AlertCenter,
    private val dao: AlertDao,
) : ViewModel() {
    val prefs: StateFlow<AlertPreferences> = settings.alerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertPreferences())
    val history: StateFlow<List<AlertEventEntity>> = dao.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _voiceStatus = MutableStateFlow<VoiceStatus?>(null)
    val voiceStatus: StateFlow<VoiceStatus?> = _voiceStatus.asStateFlow()

    init { refreshVoice() }

    fun refreshVoice() = viewModelScope.launch { _voiceStatus.value = center.voiceStatus(prefs.value.voiceLanguage) }

    fun update(transform: (AlertPreferences) -> AlertPreferences) = viewModelScope.launch {
        settings.updateAlerts(transform)
        _voiceStatus.value = center.voiceStatus(transform(prefs.value).voiceLanguage)
    }

    fun toggle(type: AlertType, enabled: Boolean) = update { it.copy(enabled = if (enabled) it.enabled + type else it.enabled - type) }

    fun testVoice() = viewModelScope.launch { center.testVoice(prefs.value.voiceLanguage) }

    fun message(e: AlertEventEntity): String = runCatching {
        center.message(Alert(AlertType.valueOf(e.type), com.jacdrive.core.model.AlertSeverity.valueOf(e.severity), e.value, e.detail))
    }.getOrDefault(e.type)

    fun clearHistory() = viewModelScope.launch { dao.clear() }
}
