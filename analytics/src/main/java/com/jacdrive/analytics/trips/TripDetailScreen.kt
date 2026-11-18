package com.jacdrive.analytics.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.analytics.R
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.util.Num
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.MetricReadout
import com.jacdrive.ui.components.ScreenHeader
import java.text.DateFormat
import java.util.Date

@Composable
fun TripDetailScreen(id: Long, onBack: () -> Unit, viewModel: TripsViewModel = hiltViewModel()) {
    val trip by remember(id) { viewModel.trip(id) }.collectAsStateWithLifecycle(null)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val t = trip
    val u = state.speedUnit
    val dist = if (u == SpeedUnit.KMH) "km" else "mi"
    val spd = if (u == SpeedUnit.KMH) "km/h" else "mph"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                t?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(it.startMs)) } ?: stringResource(R.string.trips_title),
                subtitle = t?.let { stringResource(R.string.trip_until, DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(it.endMs))) },
                onBack = onBack,
                actions = { IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.trip_delete)) } },
            )
        }
        if (t != null) {
            item {
                JacCard {
                    Row {
                        MetricReadout(stringResource(R.string.trips_distance), Num.two(UnitConverter.distance(t.distanceKm, u)), dist, Modifier.weight(1f))
                        MetricReadout(stringResource(R.string.trips_time), "${t.durationS / 60}", "min", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        MetricReadout(stringResource(R.string.trips_avg_speed), Num.int(UnitConverter.speed(t.avgSpeedKmh, u)), spd, Modifier.weight(1f))
                        MetricReadout(stringResource(R.string.perf_max_speed), Num.int(UnitConverter.speed(t.maxSpeedKmh, u)), spd, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        MetricReadout(stringResource(R.string.trip_avg_rpm), t.avgRpm?.let(Num::int) ?: "—", "rpm", Modifier.weight(1f))
                        MetricReadout(stringResource(R.string.trip_moving), "${t.movingS / 60}", "min", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        MetricReadout(stringResource(R.string.trip_fuel_used), t.fuelUsedL?.let(Num::two) ?: "—", "L", Modifier.weight(1f))
                        MetricReadout(stringResource(R.string.trips_consumption), t.consumptionL100?.let(Num::one) ?: "—", "L/100km", Modifier.weight(1f))
                    }
                }
            }
            item {
                InfoNote(
                    stringResource(
                        when (t.fuelSource) {
                            "ECU_FUEL_RATE" -> R.string.trip_fuel_ecu
                            "MAF_ESTIMATE" -> R.string.trip_fuel_maf
                            else -> R.string.trip_fuel_none
                        },
                    ),
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.trip_delete)) },
            confirmButton = { TextButton(onClick = { confirm = false; viewModel.delete(id); onBack() }) { Text(stringResource(R.string.trip_delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
