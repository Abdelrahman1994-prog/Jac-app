package com.jacdrive.obd.repository

import com.jacdrive.core.di.ApplicationScope
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.demo.DemoDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses the active data stream.
 *
 * Phase 1: demo stream when demo mode is enabled, otherwise an explicit OFFLINE snapshot with no
 * values. Phase 2 inserts the Bluetooth ELM327 stream, which always takes priority over demo.
 */
@Singleton
class DefaultVehicleDataRepository @Inject constructor(
    settingsRepository: SettingsRepository,
    private val demoDataSource: DemoDataSource,
    @ApplicationScope scope: CoroutineScope,
) : VehicleDataRepository {

    private data class StreamConfig(val demo: Boolean, val intervalMs: Long)

    override val snapshot: StateFlow<VehicleSnapshot> = settingsRepository.settings
        .map { StreamConfig(it.demoModeEnabled, it.pollingIntervalMs) }
        .distinctUntilChanged()
        .flatMapLatest { cfg ->
            if (cfg.demo) demoDataSource.stream(cfg.intervalMs.coerceAtLeast(DEMO_MIN_INTERVAL_MS))
            else flowOf(VehicleSnapshot.offline())
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), VehicleSnapshot.offline())

    private companion object {
        const val DEMO_MIN_INTERVAL_MS = 50L
    }
}
