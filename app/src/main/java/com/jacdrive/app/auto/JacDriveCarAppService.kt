package com.jacdrive.app.auto

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.jacdrive.core.database.AlertDao
import com.jacdrive.core.database.DiagnosticDao
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.diagnostics.data.DtcDatabaseProvider
import com.jacdrive.obd.repository.VehicleDataRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android Auto entry point. Uses only Car App Library templates, so the host enforces its
 * driver-distraction limits (item counts, refresh rate, no free drawing).
 *
 * Note: Android Auto only runs Car App Library apps installed from Google Play (including
 * Internal App Sharing / internal testing). See docs/ANDROID_AUTO.md.
 */
@AndroidEntryPoint
class JacDriveCarAppService : CarAppService() {

    @Inject lateinit var vehicleData: VehicleDataRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var diagnosticDao: DiagnosticDao
    @Inject lateinit var dtc: DtcDatabaseProvider
    @Inject lateinit var alertDao: AlertDao

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR // debug builds: Desktop Head Unit testing
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen =
            LiveScreen(carContext, CarDeps(vehicleData, settings, diagnosticDao, dtc, alertDao))
    }
}

/** Dependencies handed to the car screens. */
class CarDeps(
    val vehicleData: VehicleDataRepository,
    val settings: SettingsRepository,
    val diagnosticDao: DiagnosticDao,
    val dtc: DtcDatabaseProvider,
    val alertDao: AlertDao,
)
