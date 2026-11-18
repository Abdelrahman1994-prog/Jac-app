package com.jacdrive.app

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.model.ThemeMode
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.ui.cockpit.CockpitStyles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RootUiState(
    val loaded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentArgb: Long = 0xFF36C2FF,
)

/** Drives app-wide theming: light/dark mode and the accent colour of the selected cockpit theme. */
@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<RootUiState> = combine(settings.settings, settings.cockpit) { app, cockpit ->
        RootUiState(
            loaded = true,
            themeMode = app.themeMode,
            accentArgb = CockpitStyles.resolve(cockpit).accent.toArgb().toLong() and 0xFFFFFFFFL,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootUiState())
}
