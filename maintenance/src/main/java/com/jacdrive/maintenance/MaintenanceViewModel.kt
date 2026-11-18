package com.jacdrive.maintenance

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.database.MaintenanceItemEntity
import com.jacdrive.core.database.MaintenanceRecordEntity
import com.jacdrive.maintenance.data.DueState
import com.jacdrive.maintenance.data.ItemStatus
import com.jacdrive.maintenance.data.MaintenanceRepository
import com.jacdrive.maintenance.data.MaintenanceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class MaintenanceItemUi(
    val item: MaintenanceItemEntity,
    val records: List<MaintenanceRecordEntity>,
    val status: ItemStatus,
) {
    val builtIn: ServiceItem? get() = item.builtInKey?.let { k -> ServiceItem.entries.firstOrNull { it.name == k } }
}

data class MaintenanceUiState(
    val items: List<MaintenanceItemUi> = emptyList(),
    val odometerKm: Int? = null,
    val costThisYear: Double = 0.0,
    val costAllTime: Double = 0.0,
    val currency: String = "",
    val loaded: Boolean = false,
)

@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val repository: MaintenanceRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val order = mapOf(DueState.OVERDUE to 0, DueState.DUE_SOON to 1, DueState.OK to 2, DueState.NO_BASELINE to 3, DueState.NOT_CONFIGURED to 4)

    val state: StateFlow<MaintenanceUiState> = combine(repository.items, repository.profile) { items, profile ->
        val odo = profile.currentMileageKm.takeIf { it > 0 }
        val now = System.currentTimeMillis()
        val yearStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
        val records = items.flatMap { it.records }
        MaintenanceUiState(
            items = items.map { MaintenanceItemUi(it.item, it.records.sortedByDescending { r -> r.dateMs }, MaintenanceStatus.compute(it.item, odo, now)) }
                .sortedWith(compareBy<MaintenanceItemUi> { order[it.status.state] }.thenBy { it.status.progress?.let { p -> -p } ?: 0f }.thenBy { it.item.sortOrder }),
            odometerKm = odo,
            costThisYear = records.filter { it.dateMs >= yearStart }.sumOf { it.cost ?: 0.0 },
            costAllTime = records.sumOf { it.cost ?: 0.0 },
            currency = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault(""),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaintenanceUiState())

    val notificationsAllowed: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun updateOdometer(km: Int) = viewModelScope.launch { repository.updateOdometer(km) }
    fun addCustom(name: String, km: Int?, months: Int?) = viewModelScope.launch { repository.addCustom(name, km, months) }
    fun setSchedule(id: Long, km: Int?, months: Int?, lastKm: Int?, lastDate: Long?) = viewModelScope.launch { repository.setSchedule(id, km, months, lastKm, lastDate) }
    fun logService(id: Long, date: Long, km: Int?, cost: Double?, notes: String?) = viewModelScope.launch { repository.logService(id, date, km, cost, notes) }
    fun deleteItem(id: Long) = viewModelScope.launch { repository.deleteItem(id) }
    fun deleteRecord(id: Long) = viewModelScope.launch { repository.deleteRecord(id) }
}
