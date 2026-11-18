package com.jacdrive.core.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import com.jacdrive.core.database.JacDriveDatabase
import com.jacdrive.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

data class BackupInfo(val createdMs: Long, val dbVersion: Int, val staged: File)

sealed class BackupError(message: String) : Exception(message) {
    class NotABackup : BackupError("not a JAC DRIVE backup")
    class NewerVersion(val version: Int) : BackupError("backup from a newer app version ($version)")
    class Corrupt(detail: String) : BackupError("corrupt backup: $detail")
}

/**
 * Backup = zip of `manifest.json`, the Room database (after a WAL checkpoint) and the settings
 * DataStore file. Restore validates everything before touching live data, then restarts the app.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: JacDriveDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dbFile get() = context.getDatabasePath(JacDriveDatabase.NAME)
    private val settingsFile get() = File(context.filesDir, "datastore/$SETTINGS_FILE")

    suspend fun export(out: OutputStream) = withContext(io) {
        val version = db.openHelper.writableDatabase.version
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                JSONObject()
                    .put("app", "JAC DRIVE")
                    .put("format", FORMAT)
                    .put("dbVersion", version)
                    .put("createdMs", System.currentTimeMillis())
                    .toString().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            if (settingsFile.exists()) {
                zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
                settingsFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Unpacks and checks a backup into a staging folder without touching live data. */
    suspend fun validate(input: InputStream): BackupInfo = withContext(io) {
        val staged = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var manifest: JSONObject? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                when (e.name) {
                    MANIFEST -> manifest = runCatching { JSONObject(zip.readBytes().decodeToString()) }.getOrNull()
                    DB_ENTRY, SETTINGS_ENTRY -> File(staged, e.name).outputStream().use { zip.copyTo(it) }
                }
            }
        }
        val m = manifest ?: throw BackupError.NotABackup()
        if (m.optString("app") != "JAC DRIVE") throw BackupError.NotABackup()
        val stagedDb = File(staged, DB_ENTRY)
        if (!stagedDb.exists()) throw BackupError.Corrupt("database missing")
        val current = db.openHelper.readableDatabase.version
        val version = try {
            SQLiteDatabase.openDatabase(stagedDb.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
                d.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst(); if (c.getString(0) != "ok") throw BackupError.Corrupt(c.getString(0)) }
                d.version
            }
        } catch (e: BackupError) {
            throw e
        } catch (e: Exception) {
            throw BackupError.Corrupt(e.message ?: "unreadable")
        }
        if (version > current) throw BackupError.NewerVersion(version)
        BackupInfo(m.optLong("createdMs"), version, staged)
    }

    /**
     * Replaces live data with a validated backup and restarts the app. Older database versions are
     * upgraded by the normal Room migrations on the next start.
     */
    suspend fun restoreAndRestart(info: BackupInfo) {
        withContext(io) {
            db.close()
            listOf("", "-wal", "-shm").forEach { File(dbFile.path + it).delete() }
            File(info.staged, DB_ENTRY).copyTo(dbFile, overwrite = true)
            File(info.staged, SETTINGS_ENTRY).takeIf { it.exists() }?.let {
                settingsFile.parentFile?.mkdirs()
                it.copyTo(settingsFile, overwrite = true)
            }
            info.staged.deleteRecursively()
        }
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        exitProcess(0)
    }

    companion object {
        const val FORMAT = 1
        const val MANIFEST = "manifest.json"
        const val DB_ENTRY = "jacdrive.db"
        const val SETTINGS_ENTRY = "settings.preferences_pb"
        const val SETTINGS_FILE = "jacdrive_settings.preferences_pb"
    }
}
