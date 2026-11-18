package com.jacdrive.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A fired alert, kept for the in-app history. */
@Entity(tableName = "alert_event", indices = [Index("timestampMs")])
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    /** [com.jacdrive.core.model.AlertType] name. */
    val type: String,
    val severity: String,
    val value: Double?,
    val detail: String?,
    val spoken: Boolean,
)

@Dao
interface AlertDao {
    @Insert suspend fun insert(e: AlertEventEntity): Long
    @Query("SELECT * FROM alert_event ORDER BY timestampMs DESC LIMIT 500") fun observeRecent(): Flow<List<AlertEventEntity>>
    @Query("DELETE FROM alert_event") suspend fun clear()
    /** Keeps the history bounded. */
    @Query("DELETE FROM alert_event WHERE id NOT IN (SELECT id FROM alert_event ORDER BY timestampMs DESC LIMIT 1000)") suspend fun trim()
}
