package com.jacdrive.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jacdrive.core.backup.BackupError
import com.jacdrive.core.backup.BackupManager
import com.jacdrive.core.database.ALL_MIGRATIONS
import com.jacdrive.core.database.JacDriveDatabase
import com.jacdrive.core.database.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun db() = Room.databaseBuilder(context, JacDriveDatabase::class.java, JacDriveDatabase.NAME)
        .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()

    @Test fun exportThenValidateRoundTrip() = runBlocking {
        val db = db()
        db.tripDao().insert(TripEntity(startMs = 1, endMs = 2, distanceKm = 12.5, durationS = 600, movingS = 500, maxSpeedKmh = 90.0, avgRpm = null, fuelUsedL = null, fuelSource = null, completed = true))
        val manager = BackupManager(context, db, Dispatchers.IO)
        val bytes = ByteArrayOutputStream().also { manager.export(it) }.toByteArray()
        val info = manager.validate(ByteArrayInputStream(bytes))
        assertEquals(db.openHelper.readableDatabase.version, info.dbVersion)
        assertTrue(info.createdMs > 0)
        db.close()
    }

    @Test fun rejectsForeignZip() = runBlocking {
        val db = db()
        val zip = ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { it.putNextEntry(ZipEntry("x.txt")); it.write(1); it.closeEntry() } }.toByteArray()
        try {
            BackupManager(context, db, Dispatchers.IO).validate(ByteArrayInputStream(zip))
            fail()
        } catch (e: BackupError) {
            assertTrue(e is BackupError.NotABackup)
        } finally { db.close() }
    }
}
