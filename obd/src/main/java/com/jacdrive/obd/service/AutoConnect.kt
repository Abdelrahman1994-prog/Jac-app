package com.jacdrive.obd.service

import android.content.Context
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.bluetooth.AdapterDevice
import com.jacdrive.obd.bluetooth.BluetoothDevices
import com.jacdrive.obd.bluetooth.BluetoothPermissions
import com.jacdrive.obd.session.ObdConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Reconnects to the last adapter when the app comes to the foreground, if the user allows it. */
@Singleton
class AutoConnect @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val manager: ObdConnectionManager,
    private val bluetooth: BluetoothDevices,
) {
    private var attemptedThisProcess = false

    suspend fun onAppForeground() {
        if (attemptedThisProcess) return
        val s = settings.settings.first()
        val address = s.adapterAddress ?: return
        if (!s.autoReconnect || manager.state.value.active) return
        if (!BluetoothPermissions.hasAll(context) || !bluetooth.isEnabled()) return
        attemptedThisProcess = true
        runCatching { ObdLinkService.connect(context, AdapterDevice(address, s.adapterName)) }
    }
}
