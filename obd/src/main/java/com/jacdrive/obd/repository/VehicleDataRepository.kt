package com.jacdrive.obd.repository

import com.jacdrive.core.model.VehicleSnapshot
import kotlinx.coroutines.flow.StateFlow

/** Single source of truth for live vehicle data consumed by every screen. */
interface VehicleDataRepository {
    val snapshot: StateFlow<VehicleSnapshot>
}
