package com.jacdrive.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Single local database. Later phases add trips, maintenance, diagnostic sessions and alert
 * history as new entities with explicit migrations (schemas are exported to `core/schemas`).
 */
@Database(
    entities = [VehicleProfileEntity::class, DiagnosticSessionEntity::class, DiagnosticCodeEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class JacDriveDatabase : RoomDatabase() {
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        const val NAME = "jacdrive.db"
    }
}
