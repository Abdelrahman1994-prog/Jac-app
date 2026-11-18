package com.jacdrive.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jacdrive.diagnostics.data.DeductionReason
import com.jacdrive.diagnostics.data.DtcInfo
import com.jacdrive.diagnostics.data.DtcSeverity
import com.jacdrive.diagnostics.data.DtcSystem
import com.jacdrive.obd.dtc.Monitor
import com.jacdrive.obd.dtc.MonitorState
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout
import java.text.DateFormat
import java.util.Date

@Composable
fun isArabic(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

@Composable
fun formatDate(ms: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(ms))
}

@Composable
fun severityColor(s: DtcSeverity): Color = when (s) {
    DtcSeverity.CRITICAL -> JacTheme.status.critical
    DtcSeverity.HIGH -> Color(0xFFFF7A3D)
    DtcSeverity.MEDIUM -> JacTheme.status.warning
    DtcSeverity.LOW -> JacTheme.status.info
    DtcSeverity.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun severityLabel(s: DtcSeverity): String = stringResource(
    when (s) {
        DtcSeverity.CRITICAL -> R.string.sev_critical
        DtcSeverity.HIGH -> R.string.sev_high
        DtcSeverity.MEDIUM -> R.string.sev_medium
        DtcSeverity.LOW -> R.string.sev_low
        DtcSeverity.UNKNOWN -> R.string.sev_unknown
    },
)

@Composable
fun severityAdvice(s: DtcSeverity): String = stringResource(
    when (s) {
        DtcSeverity.CRITICAL -> R.string.sev_critical_advice
        DtcSeverity.HIGH -> R.string.sev_high_advice
        DtcSeverity.MEDIUM -> R.string.sev_medium_advice
        DtcSeverity.LOW -> R.string.sev_low_advice
        DtcSeverity.UNKNOWN -> R.string.sev_unknown_advice
    },
)

@Composable
fun systemLabel(s: DtcSystem): String = stringResource(
    when (s) {
        DtcSystem.FUEL_AIR -> R.string.sys_fuel_air
        DtcSystem.IGNITION -> R.string.sys_ignition
        DtcSystem.EMISSIONS -> R.string.sys_emissions
        DtcSystem.COOLING -> R.string.sys_cooling
        DtcSystem.SPEED_IDLE -> R.string.sys_speed_idle
        DtcSystem.ECU -> R.string.sys_ecu
        DtcSystem.TRANSMISSION -> R.string.sys_transmission
        DtcSystem.NETWORK -> R.string.sys_network
        DtcSystem.ELECTRICAL -> R.string.sys_electrical
        DtcSystem.ENGINE -> R.string.sys_engine
        DtcSystem.CHASSIS -> R.string.sys_chassis
        DtcSystem.BODY -> R.string.sys_body
        DtcSystem.UNKNOWN -> R.string.sys_unknown
    },
)

@Composable
fun monitorLabel(m: Monitor): String = stringResource(
    when (m) {
        Monitor.MISFIRE -> R.string.mon_misfire
        Monitor.FUEL_SYSTEM -> R.string.mon_fuel
        Monitor.COMPONENTS -> R.string.mon_components
        Monitor.CATALYST -> R.string.mon_catalyst
        Monitor.HEATED_CATALYST -> R.string.mon_heated_catalyst
        Monitor.EVAP_SYSTEM -> R.string.mon_evap
        Monitor.SECONDARY_AIR -> R.string.mon_secondary_air
        Monitor.OXYGEN_SENSOR -> R.string.mon_o2
        Monitor.OXYGEN_SENSOR_HEATER -> R.string.mon_o2_heater
        Monitor.EGR_VVT -> R.string.mon_egr
        Monitor.NMHC_CATALYST -> R.string.mon_nmhc
        Monitor.NOX_SCR -> R.string.mon_nox
        Monitor.BOOST_PRESSURE -> R.string.mon_boost
        Monitor.EXHAUST_GAS_SENSOR -> R.string.mon_exhaust_sensor
        Monitor.PM_FILTER -> R.string.mon_pm_filter
    },
)

@Composable
fun deductionLabel(r: DeductionReason): String = stringResource(
    when (r) {
        DeductionReason.MIL_ON -> R.string.ded_mil
        DeductionReason.STORED_CODES -> R.string.ded_stored
        DeductionReason.PENDING_CODES -> R.string.ded_pending
        DeductionReason.PERMANENT_CODES -> R.string.ded_permanent
        DeductionReason.READINESS_INCOMPLETE -> R.string.ded_readiness
        DeductionReason.COOLANT_HIGH -> R.string.ded_coolant
        DeductionReason.BATTERY_ABNORMAL -> R.string.ded_battery
        DeductionReason.CRITICAL_CODE -> R.string.ded_critical
    },
)

/** One fault-code row: code, localized title, severity. */
@Composable
fun DtcRow(info: DtcInfo, onClick: () -> Unit) {
    val ar = isArabic()
    val color = severityColor(info.severity)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).size(width = 4.dp, height = 40.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(info.code, style = Readout.Small, color = color)
            Text(info.title.get(ar), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(severityLabel(info.severity), color)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ReadinessGrid(readiness: Map<Monitor, MonitorState>) {
    val s = JacTheme.status
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        readiness.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (m, st) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (st == MonitorState.COMPLETE) Icons.Rounded.CheckCircle else Icons.Rounded.Pending, null,
                            tint = if (st == MonitorState.COMPLETE) s.ok else s.warning, modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(monitorLabel(m), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
