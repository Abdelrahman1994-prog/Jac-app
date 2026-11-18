package com.jacdrive.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2: diagnostic sessions and codes (Phase 3). Existing vehicle profile is untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `diagnostic_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestampMs` INTEGER NOT NULL, `kind` TEXT NOT NULL, `milOn` INTEGER, `reportedDtcCount` INTEGER, " +
                "`permanentSupported` INTEGER NOT NULL, `readiness` TEXT, `failedReads` TEXT, `protocol` TEXT, " +
                "`adapterName` TEXT, `mileageKm` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `diagnostic_code` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `code` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `diagnostic_session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_code_sessionId` ON `diagnostic_code` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_diagnostic_code_code` ON `diagnostic_code` (`code`)")
    }
}

/** v2 → v3: trips, performance runs and maintenance (Phase 4). SQL taken from the exported schema. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `trip` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `distanceKm` REAL NOT NULL, `durationS` INTEGER NOT NULL, `movingS` INTEGER NOT NULL, `maxSpeedKmh` REAL NOT NULL, `avgRpm` REAL, `fuelUsedL` REAL, `fuelSource` TEXT, `completed` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_startMs` ON `trip` (`startMs`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `performance_run` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestampMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `timeS` REAL NOT NULL, `peakG` REAL, `maxSpeedKmh` REAL NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_performance_run_type` ON `performance_run` (`type`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `maintenance_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `builtInKey` TEXT, `customName` TEXT, `intervalKm` INTEGER, `intervalMonths` INTEGER, `lastServiceKm` INTEGER, `lastServiceDateMs` INTEGER, `enabled` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `maintenance_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, `dateMs` INTEGER NOT NULL, `odometerKm` INTEGER, `cost` REAL, `notes` TEXT, FOREIGN KEY(`itemId`) REFERENCES `maintenance_item`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_record_itemId` ON `maintenance_record` (`itemId`)")
    }
}

/** v3 → v4: alert history (Phase 5). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `alert_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestampMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `severity` TEXT NOT NULL, `value` REAL, `detail` TEXT, `spoken` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_alert_event_timestampMs` ON `alert_event` (`timestampMs`)")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
