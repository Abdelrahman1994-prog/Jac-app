package com.jacdrive.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A drive recorded from real OBD data. Demo data is never recorded. */
@Entity(tableName = "trip", indices = [Index("startMs")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val distanceKm: Double,
    /** Engine-running time. */
    val durationS: Long,
    /** Time with speed > 0. */
    val movingS: Long,
    val maxSpeedKmh: Double,
    val avgRpm: Double?,
    /** Litres, only when the ECU provides fuel rate or MAF. */
    val fuelUsedL: Double?,
    /** "ECU_FUEL_RATE" or "MAF_ESTIMATE", null when no fuel data. */
    val fuelSource: String?,
    /** False while the trip is still being recorded (checkpointed). */
    val completed: Boolean,
) {
    val avgSpeedKmh: Double get() = if (durationS > 0) distanceKm / (durationS / 3600.0) else 0.0
    val consumptionL100: Double? get() = fuelUsedL?.takeIf { distanceKm >= 1.0 }?.let { it / distanceKm * 100 }
}

@Dao
interface TripDao {
    @Insert suspend fun insert(trip: TripEntity): Long
    @Update suspend fun update(trip: TripEntity)
    @Query("SELECT * FROM trip ORDER BY startMs DESC") fun observeAll(): Flow<List<TripEntity>>
    @Query("SELECT * FROM trip WHERE startMs >= :fromMs ORDER BY startMs ASC") fun observeSince(fromMs: Long): Flow<List<TripEntity>>
    @Query("SELECT * FROM trip ORDER BY startMs ASC") suspend fun all(): List<TripEntity>
    @Query("SELECT * FROM trip WHERE id = :id") fun observe(id: Long): Flow<TripEntity?>
    @Query("DELETE FROM trip WHERE id = :id") suspend fun delete(id: Long)
    /** Trips left incomplete by a crash or kill are closed with their last checkpoint. */
    @Query("UPDATE trip SET completed = 1 WHERE completed = 0") suspend fun closeDangling()
}

/** A saved acceleration run (track use only). */
@Entity(tableName = "performance_run", indices = [Index("type")])
data class PerformanceRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    /** "ZERO_60", "ZERO_100" or "SIXTY_120". */
    val type: String,
    val timeS: Double,
    val peakG: Double?,
    val maxSpeedKmh: Double,
)

@Dao
interface PerformanceDao {
    @Insert suspend fun insert(run: PerformanceRunEntity): Long
    @Query("SELECT * FROM performance_run ORDER BY timestampMs DESC") fun observeAll(): Flow<List<PerformanceRunEntity>>
    @Query("DELETE FROM performance_run WHERE id = :id") suspend fun delete(id: Long)
}

/** A service item. Built-in items carry [builtInKey]; intervals always come from the user. */
@Entity(tableName = "maintenance_item")
data class MaintenanceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val builtInKey: String?,
    val customName: String?,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    val lastServiceKm: Int?,
    val lastServiceDateMs: Long?,
    val enabled: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "maintenance_record",
    foreignKeys = [ForeignKey(MaintenanceItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")],
)
data class MaintenanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val dateMs: Long,
    val odometerKm: Int?,
    val cost: Double?,
    val notes: String?,
)

data class ItemWithRecords(
    @Embedded val item: MaintenanceItemEntity,
    @Relation(parentColumn = "id", entityColumn = "itemId") val records: List<MaintenanceRecordEntity>,
)

@Dao
interface MaintenanceDao {
    @androidx.room.Transaction
    @Query("SELECT * FROM maintenance_item ORDER BY sortOrder, id") fun observeItems(): Flow<List<ItemWithRecords>>
    @androidx.room.Transaction
    @Query("SELECT * FROM maintenance_item ORDER BY sortOrder, id") suspend fun items(): List<ItemWithRecords>
    @Query("SELECT COUNT(*) FROM maintenance_item WHERE builtInKey IS NOT NULL") suspend fun builtInCount(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItems(items: List<MaintenanceItemEntity>)
    @Upsert suspend fun upsertItem(item: MaintenanceItemEntity): Long
    @Query("SELECT * FROM maintenance_item WHERE id = :id") suspend fun item(id: Long): MaintenanceItemEntity?
    @Query("DELETE FROM maintenance_item WHERE id = :id") suspend fun deleteItem(id: Long)
    @Insert suspend fun insertRecord(record: MaintenanceRecordEntity): Long
    @Query("DELETE FROM maintenance_record WHERE id = :id") suspend fun deleteRecord(id: Long)
    @Query("SELECT * FROM maintenance_record ORDER BY dateMs DESC") fun observeRecords(): Flow<List<MaintenanceRecordEntity>>
}
