package com.jacdrive.analytics.trips

import com.jacdrive.core.database.TripDao
import com.jacdrive.core.di.ApplicationScope
import com.jacdrive.obd.repository.VehicleDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records trips in the background for the lifetime of the app process (kept alive by the OBD
 * foreground service while connected). In-progress trips are checkpointed every 30 s so a kill
 * loses at most 30 s of data.
 */
@Singleton
class TripRecorder @Inject constructor(
    private val vehicleData: VehicleDataRepository,
    private val dao: TripDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val tracker = TripTracker()
    private val mutex = Mutex()
    private var currentId: Long? = null
    private var started = false

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            dao.closeDangling()
            val ticker = flow { while (true) { emit(Unit); delay(5_000) } }
            merge(vehicleData.snapshot.map { it }, ticker.map { vehicleData.snapshot.value })
                .collect { s -> handle(tracker.onSnapshot(s, System.currentTimeMillis())) }
        }
    }

    private suspend fun handle(event: TripTracker.Event?) = mutex.withLock {
        when (event) {
            null -> Unit
            is TripTracker.Event.Started -> {
                currentId = dao.insert(event.trip)
                _recording.value = true
            }
            is TripTracker.Event.Checkpoint -> currentId?.let { dao.update(event.trip.copy(id = it)) }
            is TripTracker.Event.Finished -> {
                currentId?.let { dao.update(event.trip.copy(id = it)) }
                currentId = null
                _recording.value = false
            }
            TripTracker.Event.Discarded -> {
                currentId?.let { dao.delete(it) }
                currentId = null
                _recording.value = false
            }
        }
    }
}
