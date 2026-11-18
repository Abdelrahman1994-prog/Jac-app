package com.jacdrive.core

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jacdrive.core.database.JacDriveDatabase
import com.jacdrive.core.database.MIGRATION_1_2
import com.jacdrive.core.database.MIGRATION_2_3
import com.jacdrive.core.database.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verifies the v1 → v2 migration against Room's exported schemas and keeps existing data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), JacDriveDatabase::class.java)

    @Test fun migrate1To2KeepsVehicleProfile() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO vehicle_profile (id, name, make, model, modelYear, trim, currentMileageKm, fuelType, updatedAtMs) " +
                    "VALUES (1, 'JAC J7', 'JAC', 'J7', 2026, 'Ultimate', 12345, 'PETROL', 0)",
            )
        }
        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)
        db.query("SELECT currentMileageKm FROM vehicle_profile WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(12345, it.getInt(0))
        }
        db.execSQL("INSERT INTO diagnostic_session (timestampMs, kind, permanentSupported) VALUES (1, 'SCAN', 0)")
        db.execSQL("INSERT INTO diagnostic_code (sessionId, code, type) VALUES (1, 'P0301', 'STORED')")
    }

    @Test fun migrateAllTheWayKeepsDiagnostics() {
        helper.createDatabase(DB2, 2).use { db ->
            db.execSQL("INSERT INTO diagnostic_session (timestampMs, kind, permanentSupported) VALUES (5, 'SCAN', 0)")
        }
        val db = helper.runMigrationsAndValidate(DB2, 4, true, MIGRATION_2_3, MIGRATION_3_4)
        db.query("SELECT COUNT(*) FROM diagnostic_session").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO trip (startMs, endMs, distanceKm, durationS, movingS, maxSpeedKmh, completed) VALUES (1, 2, 3.5, 60, 50, 80, 1)")
        db.execSQL("INSERT INTO maintenance_item (builtInKey, enabled, sortOrder) VALUES ('ENGINE_OIL', 1, 0)")
        db.execSQL("INSERT INTO maintenance_record (itemId, dateMs) VALUES (1, 0)")
        db.execSQL("INSERT INTO alert_event (timestampMs, type, severity, spoken) VALUES (1, 'COOLANT_HIGH', 'CRITICAL', 1)")
    }

    private companion object {
        const val DB2 = "migration-test-2"
        const val DB = "migration-test"
        @Suppress("unused") val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    }
}
