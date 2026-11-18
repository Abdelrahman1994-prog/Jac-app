package com.jacdrive.obd.session

import com.jacdrive.core.database.VehicleProfileDao
import com.jacdrive.core.database.VehicleProfileEntity
import com.jacdrive.core.di.ApplicationScope
import com.jacdrive.core.di.IoDispatcher
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.bluetooth.AdapterDevice
import com.jacdrive.obd.bluetooth.ObdTransport
import com.jacdrive.obd.bluetooth.ObdTransportFactory
import com.jacdrive.obd.elm.ObdException
import com.jacdrive.obd.elm.ObdFailure
import com.jacdrive.obd.elm.StreamElmChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the UI needs to know about the adapter link. */
data class ObdLinkState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val device: AdapterDevice? = null,
    val step: InitStep? = null,
    val adapter: AdapterInfo? = null,
    val supportedPids: Set<Int> = emptySet(),
    val stats: SessionStats? = null,
    val snapshot: VehicleSnapshot? = null,
    val lastFailure: ObdFailure? = null,
    val reconnectAttempt: Int = 0,
    /** True while a connection is wanted (connected, connecting or waiting to retry). */
    val active: Boolean = false,
)

/**
 * Owns the single adapter connection: open transport → initialise ELM327 → discover PIDs →
 * poll. On link loss it retries with backoff while auto-reconnect is on; a user disconnect
 * always stops retrying.
 */
@Singleton
class ObdConnectionManager @Inject constructor(
    private val transportFactory: ObdTransportFactory,
    private val settings: SettingsRepository,
    private val vehicleDao: VehicleProfileDao,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(ObdLinkState())
    val state: StateFlow<ObdLinkState> = _state.asStateFlow()

    private val pollingInterval: StateFlow<Long> = settings.settings.map { it.pollingIntervalMs }
        .stateIn(scope, SharingStarted.Eagerly, 250L)

    private var job: Job? = null
    private var transport: ObdTransport? = null

    fun connect(device: AdapterDevice) {
        job?.cancel()
        _state.value = ObdLinkState(device = device, active = true, connection = ConnectionState.Connecting("bluetooth"))
        job = scope.launch { connectLoop(device) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        closeTransport()
        _state.update { ObdLinkState(device = it.device, adapter = it.adapter) }
    }

    private suspend fun connectLoop(device: AdapterDevice) {
        var attempt = 0
        while (scope.isActive) {
            try {
                runOnce(device)
            } catch (e: CancellationException) {
                closeTransport()
                throw e
            } catch (e: ObdException) {
                closeTransport()
                val failure = e.failure
                val retry = failure.isRetryable() && settings.settings.first().autoReconnect
                _state.update {
                    it.copy(
                        connection = ConnectionState.Failed(failure.name),
                        lastFailure = failure,
                        snapshot = null,
                        step = null,
                        reconnectAttempt = attempt + 1,
                        active = retry,
                    )
                }
                if (!retry) return
                delay(backoffMs(failure, attempt++))
            } catch (e: Exception) {
                closeTransport()
                _state.update { it.copy(connection = ConnectionState.Failed(e.message ?: "error"), lastFailure = ObdFailure.LINK_LOST, active = false) }
                return
            }
        }
    }

    private suspend fun runOnce(device: AdapterDevice) {
        _state.update { it.copy(connection = ConnectionState.Connecting("bluetooth"), step = null, snapshot = null) }
        val t = transportFactory.create(device)
        transport = t
        t.open()
        val channel = StreamElmChannel(t.input, t.output, io)

        val init = ElmInitializer(channel).run { step ->
            _state.update { it.copy(step = step, connection = ConnectionState.Connecting(step.name)) }
        }
        val connected = ConnectionState.Connected(device.name ?: device.address, init.adapter.protocolName)
        _state.update {
            it.copy(connection = connected, adapter = init.adapter, supportedPids = init.supportedPids, step = null, lastFailure = null, reconnectAttempt = 0)
        }
        remember(device)

        ObdSession(channel, init.supportedPids, connected, { pollingInterval.value }).run().collect { u ->
            _state.update { it.copy(snapshot = u.snapshot, stats = u.stats) }
        }
    }

    private suspend fun remember(device: AdapterDevice) {
        settings.setAdapter(device.address, device.name)
        val profile = vehicleDao.get() ?: VehicleProfileEntity.defaultJacJ7()
        vehicleDao.upsert(profile.copy(adapterName = device.name, adapterAddress = device.address, updatedAtMs = System.currentTimeMillis()))
    }

    private fun closeTransport() {
        transport?.close()
        transport = null
    }

    private fun ObdFailure.isRetryable() = when (this) {
        ObdFailure.PERMISSION_MISSING, ObdFailure.BLUETOOTH_UNAVAILABLE, ObdFailure.UNSUPPORTED_TRANSPORT -> false
        else -> true
    }

    /** Fast retries for a dropped link, slow ones while the ignition is off or the adapter is unplugged. */
    private fun backoffMs(failure: ObdFailure, attempt: Int): Long = when (failure) {
        ObdFailure.LINK_LOST -> listOf(1_000L, 3_000L, 5_000L, 10_000L).getOrElse(attempt) { 15_000L }
        else -> listOf(5_000L, 10_000L).getOrElse(attempt) { 20_000L }
    }
}
