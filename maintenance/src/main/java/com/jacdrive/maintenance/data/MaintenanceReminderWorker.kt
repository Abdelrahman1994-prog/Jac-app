package com.jacdrive.maintenance.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jacdrive.maintenance.R
import com.jacdrive.maintenance.ServiceItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily check that notifies once per item when it becomes due soon or overdue. A notification is
 * not repeated for the same item, state and due point, to avoid excessive alerts.
 */
@HiltWorker
class MaintenanceReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MaintenanceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val (items, odometer) = repository.snapshot()
        val prefs = applicationContext.getSharedPreferences("maintenance_reminders", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val due = items.mapNotNull { iw ->
            val st = MaintenanceStatus.compute(iw.item, odometer, now)
            if (st.state != DueState.DUE_SOON && st.state != DueState.OVERDUE) return@mapNotNull null
            val key = "n_${iw.item.id}_${st.state}_${st.nextDueKm}_${st.nextDueDateMs}"
            if (prefs.getBoolean(key, false)) return@mapNotNull null
            prefs.edit().putBoolean(key, true).apply()
            Triple(iw.item, st, name(iw.item.builtInKey, iw.item.customName))
        }
        if (due.isNotEmpty() && canNotify()) notify(due.map { it.third }, due.any { it.second.state == DueState.OVERDUE })
        return Result.success()
    }

    private fun name(key: String?, custom: String?): String =
        key?.let { k -> ServiceItem.entries.firstOrNull { it.name == k }?.let { applicationContext.getString(it.title) } } ?: custom.orEmpty()

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(names: List<String>, overdue: Boolean) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.mnt_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_service)
            .setContentTitle(applicationContext.getString(if (overdue) R.string.mnt_notif_overdue else R.string.mnt_notif_due))
            .setContentText(names.joinToString(" · "))
            .setAutoCancel(true)
            .setContentIntent(launch?.let { PendingIntent.getActivity(applicationContext, 2, it, PendingIntent.FLAG_IMMUTABLE) })
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL = "maintenance"
        private const val NOTIFICATION_ID = 7101
        private const val WORK = "maintenance-reminders"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceReminderWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
