package com.jacdrive.analytics.trips

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.database.TripDao
import com.jacdrive.core.database.TripEntity
import com.jacdrive.core.di.IoDispatcher
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TripsUiState(
    val trips: List<TripEntity> = emptyList(),
    val period: StatsPeriod = StatsPeriod.DAYS_7,
    val metric: StatsMetric = StatsMetric.DISTANCE,
    val buckets: List<Bucket> = emptyList(),
    val summary: Bucket? = null,
    val recording: Boolean = false,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val exportResult: Boolean? = null,
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val dao: TripDao,
    recorder: TripRecorder,
    settings: SettingsRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val selection = MutableStateFlow(Triple(StatsPeriod.DAYS_7, StatsMetric.DISTANCE, null as Boolean?))

    val state: StateFlow<TripsUiState> = combine(dao.observeAll(), selection, recorder.recording, settings.settings) { trips, sel, rec, s ->
        val buckets = TripStats.buckets(trips, sel.first, System.currentTimeMillis())
        TripsUiState(trips, sel.first, sel.second, buckets, TripStats.summary(buckets), rec, s.speedUnit, sel.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    fun setPeriod(p: StatsPeriod) = selection.update { it.copy(first = p) }
    fun setMetric(m: StatsMetric) = selection.update { it.copy(second = m) }
    fun trip(id: Long) = dao.observe(id)
    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    fun export(uri: Uri) = viewModelScope.launch {
        val ok = runCatching {
            withContext(io) {
                val csv = TripCsv.build(dao.all().filter { it.completed })
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
            }
        }.isSuccess
        selection.update { it.copy(third = ok) }
    }

    fun clearExportResult() = selection.update { it.copy(third = null) }
}
