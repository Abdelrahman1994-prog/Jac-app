package com.jacdrive.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Lists paired adapters, discovers nearby ones and starts pairing. All calls are permission-checked. */
@Singleton
@SuppressLint("MissingPermission")
class BluetoothDevices @Inject constructor(@ApplicationContext private val context: Context) {

    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isSupported: Boolean get() = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun bonded(): List<AdapterDevice> {
        if (!BluetoothPermissions.hasConnect(context)) return emptyList()
        return runCatching {
            adapter?.bondedDevices.orEmpty()
                .filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
                .map { AdapterDevice(it.address, it.name, bonded = true) }
                .sortedWith(compareByDescending<AdapterDevice> { it.looksLikeObd }.thenBy { it.name.orEmpty() })
        }.getOrDefault(emptyList())
    }

    /** Emits devices found by a Classic discovery; completes when discovery finishes (~12 s). */
    fun discover(): Flow<AdapterDevice> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val d = intent.device() ?: return
                        if (d.type == BluetoothDevice.DEVICE_TYPE_LE) return
                        trySend(AdapterDevice(d.address, runCatching { d.name }.getOrNull(), bonded = d.bondState == BluetoothDevice.BOND_BONDED))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> close()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val started = BluetoothPermissions.hasAll(context) && runCatching { adapter?.startDiscovery() == true }.getOrDefault(false)
        if (!started) close()
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            if (BluetoothPermissions.hasAll(context)) runCatching { adapter?.cancelDiscovery() }
        }
    }

    /** Emits whenever any device's bond state changes, so the paired list can refresh. */
    fun bondChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { trySend(Unit) }
        }
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /** Starts system pairing (the PIN dialog, usually 1234 or 0000 for ELM327 adapters). */
    fun pair(address: String): Boolean = BluetoothPermissions.hasConnect(context) &&
        runCatching { adapter?.getRemoteDevice(address)?.createBond() == true }.getOrDefault(false)

    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
