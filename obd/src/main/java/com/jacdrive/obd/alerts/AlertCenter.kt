package com.jacdrive.obd.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jacdrive.core.database.AlertDao
import com.jacdrive.core.database.AlertEventEntity
import com.jacdrive.core.di.ApplicationScope
import com.jacdrive.core.model.AlertSeverity
import com.jacdrive.core.model.AlertType
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.VoiceLanguage
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.R
import com.jacdrive.obd.repository.VehicleDataRepository
import com.jacdrive.obd.session.ObdConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Wires the alert engine to live data and delivers alerts as voice, notification and history. */
@Singleton
class AlertCenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleData: VehicleDataRepository,
    private val obd: ObdConnectionManager,
    private val settings: SettingsRepository,
    private val dao: AlertDao,
    private val voice: VoiceAnnouncer,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val engine = AlertEngine()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val prefs = settings.alerts.stateIn(scope, SharingStarted.Eagerly, com.jacdrive.core.model.AlertPreferences())
            launch {
                vehicleData.snapshot.collect { s ->
                    engine.onSnapshot(s, prefs.value, System.currentTimeMillis()).forEach { deliver(it) }
                }
            }
            launch {
                var wasConnected = false
                obd.state.collect { st ->
                    val connected = st.connection is ConnectionState.Connected
                    // Unexpected loss: was connected, still wanted (not a user disconnect), now failing.
                    if (wasConnected && !connected && st.active) {
                        engine.onLinkLost(prefs.value, System.currentTimeMillis())?.let { deliver(it) }
                    }
                    wasConnected = connected
                }
            }
        }
    }

    /** Used by the maintenance reminder and the "test voice" button. */
    suspend fun deliver(alert: Alert, force: Boolean = false) {
        val prefs = settings.alerts.first()
        if (!force && !prefs.isEnabled(alert.type)) return
        val locale = voiceLocale(prefs.voiceLanguage)
        val spoken = prefs.voiceEnabled && voice.speak(message(alert, localized(locale)), locale)
        if (!force) {
            dao.insert(AlertEventEntity(timestampMs = System.currentTimeMillis(), type = alert.type.name, severity = alert.severity.name, value = alert.value, detail = alert.detail, spoken = spoken))
            dao.trim()
        }
        if (prefs.notificationsEnabled && !force) notify(alert)
    }

    suspend fun testVoice(language: VoiceLanguage): Boolean {
        val locale = voiceLocale(language)
        return voice.speak(localized(locale).getString(R.string.alert_test), locale)
    }

    suspend fun voiceStatus(language: VoiceLanguage): VoiceStatus = voice.status(voiceLocale(language))

    fun voiceLocale(language: VoiceLanguage): Locale = when (language) {
        VoiceLanguage.ARABIC -> Locale("ar")
        VoiceLanguage.ENGLISH -> Locale.ENGLISH
        VoiceLanguage.APP -> AppCompatDelegate.getApplicationLocales()[0]?.takeIf { it.language == "ar" }?.let { Locale("ar") }
            ?: if (Locale.getDefault().language == "ar") Locale("ar") else Locale.ENGLISH
    }

    private fun localized(locale: Locale): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })

    fun message(alert: Alert, ctx: Context = context): String = when (alert.type) {
        AlertType.COOLANT_HIGH -> ctx.getString(R.string.alert_coolant, (alert.value ?: 0.0).toInt())
        AlertType.BATTERY_LOW -> ctx.getString(R.string.alert_battery_low, String.format(Locale.US, "%.1f", alert.value ?: 0.0))
        AlertType.BATTERY_HIGH -> ctx.getString(R.string.alert_battery_high, String.format(Locale.US, "%.1f", alert.value ?: 0.0))
        AlertType.HIGH_RPM -> ctx.getString(R.string.alert_rpm)
        AlertType.CHECK_ENGINE -> ctx.getString(R.string.alert_mil)
        AlertType.OBD_DISCONNECTED -> ctx.getString(R.string.alert_disconnected)
        AlertType.MAINTENANCE_DUE -> ctx.getString(R.string.alert_maintenance, alert.detail.orEmpty())
    }

    private fun notify(alert: Alert) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_HIGH))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        nm.notify(
            NOTIFICATION_BASE + alert.type.ordinal,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_obd)
                .setContentTitle(context.getString(R.string.alert_title))
                .setContentText(message(alert))
                .setPriority(if (alert.severity == AlertSeverity.CRITICAL) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setSilent(true) // the voice prompt is the audible part
                .setAutoCancel(true)
                .setContentIntent(launch?.let { PendingIntent.getActivity(context, 3, it, PendingIntent.FLAG_IMMUTABLE) })
                .build(),
        )
    }

    private companion object {
        const val CHANNEL = "vehicle_alerts"
        const val NOTIFICATION_BASE = 7200
    }
}
