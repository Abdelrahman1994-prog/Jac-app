package com.jacdrive.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's vehicle. Only facts the user enters (or the app measures) are stored. Technical
 * specifications that are not officially documented are left blank rather than assumed.
 */
@Entity(tableName = "vehicle_profile")
data class VehicleProfileEntity(
    @PrimaryKey val id: Long = PRIMARY_ID,
    val name: String,
    val make: String,
    val model: String,
    val modelYear: Int,
    val trim: String,
    val currentMileageKm: Int,
    val vin: String?,
    val fuelType: String,
    val engineDescription: String?,
    val transmission: String?,
    val adapterName: String?,
    val adapterAddress: String?,
    val updatedAtMs: Long,
) {
    companion object {
        const val PRIMARY_ID = 1L

        fun defaultJacJ7(nowMs: Long = System.currentTimeMillis()) = VehicleProfileEntity(
            name = "JAC J7",
            make = "JAC",
            model = "J7",
            modelYear = 2026,
            trim = "Ultimate",
            currentMileageKm = 0,
            vin = null,
            fuelType = "PETROL",
            engineDescription = null,
            transmission = null,
            adapterName = null,
            adapterAddress = null,
            updatedAtMs = nowMs,
        )
    }
}
