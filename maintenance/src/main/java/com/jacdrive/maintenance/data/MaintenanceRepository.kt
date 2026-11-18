package com.jacdrive.maintenance.data

import com.jacdrive.core.database.ItemWithRecords
import com.jacdrive.core.database.MaintenanceDao
import com.jacdrive.core.database.MaintenanceItemEntity
import com.jacdrive.core.database.MaintenanceRecordEntity
import com.jacdrive.core.database.VehicleProfileEntity
import com.jacdrive.maintenance.ServiceItem
import com.jacdrive.vehicle.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MaintenanceRepository @Inject constructor(
    private val dao: MaintenanceDao,
    private val vehicle: VehicleRepository,
) {
    /** Items with their history; the ten standard items are seeded once, without intervals. */
    val items: Flow<List<ItemWithRecords>> = dao.observeItems().onStart { seed() }

    val profile: Flow<VehicleProfileEntity> = vehicle.profile

    private suspend fun seed() {
        if (dao.builtInCount() > 0) return
        dao.insertItems(
            ServiceItem.entries.mapIndexed { i, s ->
                MaintenanceItemEntity(
                    builtInKey = s.name, customName = null, intervalKm = null, intervalMonths = null,
                    lastServiceKm = null, lastServiceDateMs = null, enabled = true, sortOrder = i,
                )
            },
        )
    }

    suspend fun setSchedule(id: Long, intervalKm: Int?, intervalMonths: Int?, lastKm: Int?, lastDateMs: Long?) {
        val item = dao.item(id) ?: return
        dao.upsertItem(item.copy(intervalKm = intervalKm, intervalMonths = intervalMonths, lastServiceKm = lastKm, lastServiceDateMs = lastDateMs))
    }

    /** Logs a service; it becomes the item's baseline if it is the most recent one. */
    suspend fun logService(id: Long, dateMs: Long, odometerKm: Int?, cost: Double?, notes: String?) {
        val item = dao.item(id) ?: return
        dao.insertRecord(MaintenanceRecordEntity(itemId = id, dateMs = dateMs, odometerKm = odometerKm, cost = cost, notes = notes?.ifBlank { null }))
        val previous = item.lastServiceDateMs
        if (previous == null || dateMs >= previous) {
            dao.upsertItem(item.copy(lastServiceDateMs = dateMs, lastServiceKm = odometerKm ?: item.lastServiceKm))
        }
        // A service logged with a higher odometer reading than the profile updates the profile.
        val p = vehicle.profile.first()
        if (odometerKm != null && odometerKm > p.currentMileageKm) vehicle.save(p.copy(currentMileageKm = odometerKm))
    }

    suspend fun addCustom(name: String, intervalKm: Int?, intervalMonths: Int?): Long =
        dao.upsertItem(
            MaintenanceItemEntity(
                builtInKey = null, customName = name.trim(), intervalKm = intervalKm, intervalMonths = intervalMonths,
                lastServiceKm = null, lastServiceDateMs = null, enabled = true, sortOrder = 100,
            ),
        )

    suspend fun deleteItem(id: Long) = dao.deleteItem(id)
    suspend fun deleteRecord(id: Long) = dao.deleteRecord(id)

    suspend fun updateOdometer(km: Int) {
        val p = vehicle.profile.first()
        vehicle.save(p.copy(currentMileageKm = km))
    }

    suspend fun snapshot(): Pair<List<ItemWithRecords>, Int?> {
        seed()
        return dao.items() to vehicle.profile.first().currentMileageKm.takeIf { it > 0 }
    }
}
