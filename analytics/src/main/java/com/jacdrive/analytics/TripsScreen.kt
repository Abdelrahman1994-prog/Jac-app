package com.jacdrive.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jacdrive.ui.components.EmptyState
import com.jacdrive.ui.components.InDevelopmentChip
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.MetricReadout
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader

@Composable
fun TripsScreen(onBack: (() -> Unit)?) {
    var period by rememberSaveable { mutableIntStateOf(1) }
    val periods = listOf(R.string.trips_day, R.string.trips_week, R.string.trips_month)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(stringResource(R.string.trips_title), subtitle = stringResource(R.string.trips_subtitle), onBack = onBack, actions = { InDevelopmentChip() })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEachIndexed { i, p -> FilterChip(period == i, { period = i }, label = { Text(stringResource(p)) }) }
            }
        }
        item {
            JacCard {
                Row {
                    MetricReadout(stringResource(R.string.trips_distance), "—", "km", Modifier.weight(1f))
                    MetricReadout(stringResource(R.string.trips_time), "—", "h", Modifier.weight(1f))
                }
                Spacer(Modifier.size(12.dp))
                Row {
                    MetricReadout(stringResource(R.string.trips_avg_speed), "—", "km/h", Modifier.weight(1f))
                    MetricReadout(stringResource(R.string.trips_consumption), "—", "L/100km", Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader(stringResource(R.string.trips_history)) }
        item {
            JacCard {
                EmptyState(Icons.Rounded.Route, stringResource(R.string.trips_empty), stringResource(R.string.trips_empty_sub)) {
                    OutlinedButton(onClick = {}, enabled = false) {
                        Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.trips_export))
                    }
                }
            }
        }
    }
}
