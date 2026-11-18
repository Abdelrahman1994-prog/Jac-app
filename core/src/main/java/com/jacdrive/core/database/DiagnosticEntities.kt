package com.jacdrive.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One diagnostic event: a fault scan or a user-confirmed clear. Only real ECU data is stored. */
@Entity(tableName = "diagnostic_session")
data class DiagnosticSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    /** "SCAN" or "CLEAR". */
    val kind: String,
    val milOn: Boolean?,
    /** Confirmed emission DTC count reported by PID 0101. */
    val reportedDtcCount: Int?,
    val permanentSupported: Boolean,
    /** Readiness monitors as "MONITOR=C|I" pairs separated by commas; null if not reported. */
    val readiness: String?,
    /** Lists that could not be read because of communication errors, comma-separated. */
    val failedReads: String?,
    val protocol: String?,
    val adapterName: String?,
    val mileageKm: Int?,
)

@Entity(
    tableName = "diagnostic_code",
    foreignKeys = [
        ForeignKey(
            entity = DiagnosticSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("code")],
)
data class DiagnosticCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val code: String,
    /** "STORED", "PENDING" or "PERMANENT". */
    val type: String,
)

data class SessionWithCodes(
    @Embedded val session: DiagnosticSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val codes: List<DiagnosticCodeEntity>,
)

@Dao
interface DiagnosticDao {
    @Insert
    suspend fun insertSession(session: DiagnosticSessionEntity): Long

    @Insert
    suspend fun insertCodes(codes: List<DiagnosticCodeEntity>)

    @Transaction
    suspend fun insert(session: DiagnosticSessionEntity, codes: List<DiagnosticCodeEntity>): Long {
        val id = insertSession(session)
        insertCodes(codes.map { it.copy(sessionId = id) })
        return id
    }

    @Transaction
    @Query("SELECT * FROM diagnostic_session ORDER BY timestampMs DESC")
    fun observeSessions(): Flow<List<SessionWithCodes>>

    @Transaction
    @Query("SELECT * FROM diagnostic_session WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionWithCodes?>

    @Query("DELETE FROM diagnostic_session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM diagnostic_session")
    suspend fun deleteAll()
}
