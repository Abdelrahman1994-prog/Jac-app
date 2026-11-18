package com.jacdrive.vehicle

import com.jacdrive.core.database.VehicleProfileDao
import com.jacdrive.core.database.VehicleProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VehicleRepository @Inject constructor(
    private val dao: VehicleProfileDao,
) {
    /** The vehicle profile; the JAC J7 2026 Ultimate defaults are seeded on first use. */
    val profile: Flow<VehicleProfileEntity> = dao.observe()
        .onStart { dao.insertIfAbsent(VehicleProfileEntity.defaultJacJ7()) }
        .filterNotNull()

    suspend fun save(profile: VehicleProfileEntity) {
        dao.upsert(profile.copy(updatedAtMs = System.currentTimeMillis()))
    }
}

/** ISO 3779 VIN format check: 17 characters, no I, O or Q. Does not verify the check digit. */
object VinValidator {
    private val pattern = Regex("^[A-HJ-NPR-Z0-9]{17}$")
    fun isValid(vin: String): Boolean = pattern.matches(vin.uppercase())
}
