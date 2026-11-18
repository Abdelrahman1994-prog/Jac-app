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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Records trips from live OBD data whenever the adapter is connected (never from demo data).
        tripRecorder.start()
        // Daily maintenance check (notifies once per item when it becomes due).
        MaintenanceReminderWorker.schedule(this)
    }
}
