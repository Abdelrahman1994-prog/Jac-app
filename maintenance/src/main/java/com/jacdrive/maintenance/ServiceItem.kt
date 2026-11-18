package com.jacdrive.maintenance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DiscFull
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TireRepair
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** The standard service items. Intervals are intentionally not pre-filled: they must come from the owner's manual. */
enum class ServiceItem(val title: Int, val icon: ImageVector) {
    ENGINE_OIL(R.string.mnt_engine_oil, Icons.Rounded.OilBarrel),
    OIL_FILTER(R.string.mnt_oil_filter, Icons.Rounded.FilterAlt),
    AIR_FILTER(R.string.mnt_air_filter, Icons.Rounded.Air),
    CABIN_FILTER(R.string.mnt_cabin_filter, Icons.Rounded.AcUnit),
    SPARK_PLUGS(R.string.mnt_spark_plugs, Icons.Rounded.Bolt),
    BRAKE_PADS(R.string.mnt_brake_pads, Icons.Rounded.DiscFull),
    COOLANT(R.string.mnt_coolant, Icons.Rounded.WaterDrop),
    TRANSMISSION_FLUID(R.string.mnt_transmission, Icons.Rounded.Settings),
    BATTERY(R.string.mnt_battery, Icons.Rounded.BatteryChargingFull),
    TIRES(R.string.mnt_tires, Icons.Rounded.TireRepair),
}
