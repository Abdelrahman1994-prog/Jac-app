package com.jacdrive.obd.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.obd.R
import com.jacdrive.obd.bluetooth.AdapterDevice
import com.jacdrive.obd.session.ObdConnectionManager
import com.jacdrive.obd.session.ObdLinkState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Keeps the adapter connection alive while the screen is off or another app is in front, as a
 * `connectedDevice` foreground service with a persistent notification and a Disconnect action.
 * It stops itself as soon as no connection is wanted.
 */
@AndroidEntryPoint
class ObdLinkService : Service() {

    @Inject lateinit var manager: ObdConnectionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                manager.disconnect()
                stopNow()
            }
            ACTION_CONNECT -> {
                val address = intent.getStringExtra(EXTRA_ADDRESS)
                if (address == null) {
                    stopNow()
                } else {
                    goForeground(manager.state.value)
                    manager.connect(AdapterDevice(address, intent.getStringExtra(EXTRA_NAME)))
                    observe()
                }
            }
            else -> if (manager.state.value.active) { goForeground(manager.state.value); observe() } else stopNow()
        }
        return START_NOT_STICKY
    }

    private fun observe() {
        if (observer != null) return
        observer = scope.launch {
            manager.state
                .map { Triple(it.active, it.connection, it.snapshot?.get(VehicleMetric.BATTERY_VOLTAGE)?.value?.let { v -> "%.1f V".format(Locale.US, v) }) }
                .distinctUntilChanged()
                .collect { (active, _, _) ->
                    if (!active) stopNow() else notificationManager().notify(NOTIFICATION_ID, build(manager.state.value))
                }
        }
    }

    private fun goForeground(state: ObdLinkState) {
        ensureChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, build(state),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        } catch (e: Exception) {
            // Missing Bluetooth permission or background-start restriction: the connection still
            // runs while the app is visible; it just isn't protected in the background.
        }
    }

    private fun build(state: ObdLinkState) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_obd)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentTitle(
            getString(
                when (state.connection) {
                    is ConnectionState.Connected -> R.string.obd_notif_connected
                    is ConnectionState.Failed -> R.string.obd_notif_retrying
                    else -> R.string.obd_notif_connecting
                },
            ),
        )
        .setContentText(
            listOfNotNull(
                state.device?.name ?: state.device?.address,
                state.adapter?.protocolName,
                state.snapshot?.get(VehicleMetric.BATTERY_VOLTAGE)?.value?.let { "%.1f V".format(Locale.US, it) },
            ).joinToString(" · "),
        )
        .setContentIntent(
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            },
        )
        .addAction(
            0, getString(R.string.obd_notif_disconnect),
            PendingIntent.getService(
                this, 1, Intent(this, ObdLinkService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    private fun ensureChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.obd_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "obd_link"
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_CONNECT = "com.jacdrive.obd.CONNECT"
        private const val ACTION_DISCONNECT = "com.jacdrive.obd.DISCONNECT"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_NAME = "name"

        fun connect(context: Context, device: AdapterDevice) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ObdLinkService::class.java)
                    .setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_ADDRESS, device.address)
                    .putExtra(EXTRA_NAME, device.name),
            )
        }

        fun disconnect(context: Context) {
            context.startService(Intent(context, ObdLinkService::class.java).setAction(ACTION_DISCONNECT))
        }
    }
}
