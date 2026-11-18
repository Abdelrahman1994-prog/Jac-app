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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
