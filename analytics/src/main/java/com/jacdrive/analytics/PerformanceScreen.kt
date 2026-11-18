package com.jacdrive.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jacdrive.ui.components.InDevelopmentChip
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.MetricReadout
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout

@Composable
fun PerformanceScreen(onBack: (() -> Unit)?) {
    val critical = JacTheme.status.critical
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.perf_title), onBack = onBack, actions = { InDevelopmentChip() }) }
        item {
            JacCard(highlight = critical) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.GppMaybe, null, tint = critical, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.perf_warning_title), style = MaterialTheme.typography.titleMedium, color = critical)
                        Text(stringResource(R.string.perf_warning_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.perf_timers)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("0–60", "0–100", "60–120").forEach { label ->
                    JacCard(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("—.—", style = Readout.Medium)
                        Text("s", style = Readout.Unit, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Timer, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.perf_arm))
            }
        }
        item { SectionHeader(stringResource(R.string.perf_session)) }
        item {
            JacCard {
                Row {
                    MetricReadout(stringResource(R.string.perf_max_speed), "—", "km/h", Modifier.weight(1f))
                    MetricReadout(stringResource(R.string.perf_avg_speed), "—", "km/h", Modifier.weight(1f))
                    MetricReadout(stringResource(R.string.perf_gforce), "—", "g", Modifier.weight(1f))
                }
            }
        }
        item {
            Text(stringResource(R.string.perf_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
