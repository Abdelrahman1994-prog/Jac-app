package com.jacdrive.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.bluetooth.AdapterDevice
import com.jacdrive.obd.bluetooth.BluetoothDevices
import com.jacdrive.obd.bluetooth.BluetoothPermissions
import com.jacdrive.obd.service.ObdLinkService
import com.jacdrive.obd.session.ObdConnectionManager
import com.jacdrive.obd.session.ObdLinkState
import com.jacdrive.ui.components.DisplayUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BluetoothEnv(
    val supported: Boolean = true,
    val permissionsGranted: Boolean = false,
    val enabled: Boolean = false,
    val paired: List<AdapterDevice> = emptyList(),
    val discovered: List<AdapterDevice> = emptyList(),
    val scanning: Boolean = false,
)

data class AdapterUiState(
    val link: ObdLinkState = ObdLinkState(),
    val env: BluetoothEnv = BluetoothEnv(),
    val savedAddress: String? = null,
    val savedName: String? = null,
    val autoReconnect: Boolean = true,
    val units: DisplayUnits = DisplayUnits(SpeedUnit.KMH, TemperatureUnit.CELSIUS),
)

@HiltViewModel
class AdapterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetooth: BluetoothDevices,
    private val manager: ObdConnectionManager,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val env = MutableStateFlow(BluetoothEnv())
    private var scanJob: Job? = null

    val state: StateFlow<AdapterUiState> = combine(manager.state, env, settings.settings) { link, e, s ->
        AdapterUiState(link, e, s.adapterAddress, s.adapterName, s.autoReconnect, DisplayUnits(s.speedUnit, s.temperatureUnit))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdapterUiState())

    init {
        refresh()
        viewModelScope.launch { bluetooth.bondChanges().collect { refresh() } }
    }

    fun refresh() {
        env.update {
            it.copy(
                supported = bluetooth.isSupported,
                permissionsGranted = BluetoothPermissions.hasAll(context),
                enabled = bluetooth.isEnabled(),
                paired = bluetooth.bonded(),
                discovered = it.discovered.filterNot { d -> bluetooth.bonded().any { b -> b.address == d.address } },
            )
        }
    }

    fun scan() {
        if (scanJob?.isActive == true) return
        env.update { it.copy(scanning = true, discovered = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                bluetooth.discover().collect { d ->
                    if (d.bonded) return@collect
                    env.update { e ->
                        if (e.discovered.any { it.address == d.address }) e
                        else e.copy(discovered = (e.discovered + d).sortedByDescending { it.looksLikeObd })
                    }
                }
            } finally {
                env.update { it.copy(scanning = false) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
    }

    fun pair(device: AdapterDevice) {
        stopScan()
        bluetooth.pair(device.address)
    }

    fun connect(device: AdapterDevice) {
        stopScan()
        ObdLinkService.connect(context, device)
    }

    fun connectSaved() {
        val s = state.value
        val address = s.savedAddress ?: return
        connect(AdapterDevice(address, s.savedName))
    }

    fun disconnect() {
        manager.disconnect()
        ObdLinkService.disconnect(context)
    }

    fun forget() = viewModelScope.launch {
        disconnect()
        settings.setAdapter(null, null)
    }

    fun setAutoReconnect(enabled: Boolean) = viewModelScope.launch { settings.setAutoReconnect(enabled) }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
