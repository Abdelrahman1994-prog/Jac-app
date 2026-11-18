package com.jacdrive.obd.repository

import com.jacdrive.core.di.ApplicationScope
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.demo.DemoDataSource
import com.jacdrive.obd.session.ObdConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chooses the active data stream. A wanted adapter connection always wins over demo mode:
 * - adapter connected → LIVE snapshots from the ECU;
 * - adapter connecting / retrying → OFFLINE snapshot carrying that connection state (no values);
 * - no adapter wanted → demo stream if enabled, otherwise OFFLINE.
 */
@Singleton
class DefaultVehicleDataRepository @Inject constructor(
    settingsRepository: SettingsRepository,
    private val demoDataSource: DemoDataSource,
    private val obd: ObdConnectionManager,
    @ApplicationScope scope: CoroutineScope,
) : VehicleDataRepository {

    private sealed interface Source {
        data object Obd : Source
        data class Demo(val intervalMs: Long) : Source
        data object Offline : Source
    }

    override val snapshot: StateFlow<VehicleSnapshot> = combine(
        settingsRepository.settings,
        obd.state.map { it.active }.distinctUntilChanged(),
    ) { s, obdActive ->
        when {
            obdActive -> Source.Obd
            s.demoModeEnabled -> Source.Demo(s.pollingIntervalMs.coerceAtLeast(DEMO_MIN_INTERVAL_MS))
            else -> Source.Offline
        }
    }.distinctUntilChanged()
        .flatMapLatest { src ->
            when (src) {
                Source.Obd -> obd.state.map { st ->
                    st.snapshot ?: VehicleSnapshot.offline().copy(connection = st.connection)
                }
                is Source.Demo -> demoDataSource.stream(src.intervalMs)
                Source.Offline -> flowOf(VehicleSnapshot.offline())
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), VehicleSnapshot.offline())

    private companion object {
        const val DEMO_MIN_INTERVAL_MS = 50L
    }
}
