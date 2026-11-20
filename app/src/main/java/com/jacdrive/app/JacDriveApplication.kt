package com.jacdrive.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.jacdrive.analytics.trips.TripRecorder
import com.jacdrive.maintenance.data.MaintenanceReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class JacDriveApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var tripRecorder: TripRecorder
    @Inject lateinit var alertCenter: com.jacdrive.obd.alerts.AlertCenter
    @Inject lateinit var liveHistory: com.jacdrive.obd.history.LiveHistory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Records trips from live OBD data whenever the adapter is connected (never from demo data).
        tripRecorder.start()
        // Voice/notification alerts from live data (never from demo data).
        alertCenter.start()
        // Rolling 10-minute history for the dashboard trend chart and the Live Data screen.
        liveHistory.start()
        // Daily maintenance check (notifies once per item when it becomes due).
        MaintenanceReminderWorker.schedule(this)
    }
}
