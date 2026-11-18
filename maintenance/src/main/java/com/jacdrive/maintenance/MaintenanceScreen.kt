package com.jacdrive.maintenance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jacdrive.ui.components.InDevelopmentChip
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader

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

@Composable
fun MaintenanceScreen(onBack: (() -> Unit)?) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.mnt_title), subtitle = stringResource(R.string.mnt_subtitle), onBack = onBack, actions = { InDevelopmentChip() }) }
        item { InfoNote(stringResource(R.string.mnt_manual_note)) }
        item { SectionHeader(stringResource(R.string.mnt_items)) }
        items(ServiceItem.entries) { item ->
            JacCard(contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.mnt_interval_not_set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = {}, enabled = false) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mnt_add_custom))
            }
        }
    }
}
