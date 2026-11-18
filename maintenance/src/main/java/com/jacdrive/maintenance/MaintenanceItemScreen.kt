package com.jacdrive.maintenance

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import java.util.Locale

@Composable
fun MaintenanceItemScreen(id: Long, onBack: () -> Unit, viewModel: MaintenanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui = state.items.firstOrNull { it.item.id == id }
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (ui == null) {
        if (state.loaded) onBack()
        return
    }
    val it = ui.item
    var km by rememberSaveable(id) { mutableStateOf(it.intervalKm?.toString() ?: "") }
    var months by rememberSaveable(id) { mutableStateOf(it.intervalMonths?.toString() ?: "") }
    var lastKm by rememberSaveable(id, it.lastServiceKm) { mutableStateOf(it.lastServiceKm?.toString() ?: "") }
    var lastDate by rememberSaveable(id, it.lastServiceDateMs) { mutableStateOf(it.lastServiceDateMs) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                itemName(ui), onBack = onBack,
                actions = { if (ui.builtIn == null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.mnt_delete_item)) } },
            )
        }
        item {
            JacCard(highlight = statusColor(ui.status.state).takeIf { ui.status.state.ordinal >= 3 }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(statusText(ui.status), style = MaterialTheme.typography.titleMedium, color = statusColor(ui.status.state))
                        ui.status.nextDueKm?.let { Text(stringResource(R.string.mnt_next_at_km, formatKm(it)), style = MaterialTheme.typography.bodySmall) }
                        ui.status.nextDueDateMs?.let { Text(stringResource(R.string.mnt_next_on, formatDay(it)), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.mnt_schedule)) }
        item {
            JacCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(km, { v -> km = v }, stringResource(R.string.mnt_interval_km), Modifier.weight(1f), "km")
                        NumberField(months, { v -> months = v }, stringResource(R.string.mnt_interval_months), Modifier.weight(1f))
                    }
                    NumberField(lastKm, { v -> lastKm = v }, stringResource(R.string.mnt_last_km), Modifier.fillMaxWidth(), "km")
                    DateField(lastDate, { d -> lastDate = d }, stringResource(R.string.mnt_last_date), Modifier.fillMaxWidth())
                    Text(stringResource(R.string.mnt_manual_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { viewModel.setSchedule(id, km.toIntOrNull(), months.toIntOrNull(), lastKm.toIntOrNull(), lastDate) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.mnt_save)) }
                }
            }
        }
        item {
            SectionHeader(stringResource(R.string.mnt_history)) {
                TextButton(onClick = { logOpen = true }) {
                    Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.mnt_log_service))
                }
            }
        }
        if (ui.records.isEmpty()) {
            item { Text(stringResource(R.string.mnt_history_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(ui.records, key = { r -> r.id }) { r ->
                JacCard(contentPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatDay(r.dateMs), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    r.odometerKm?.let { "${formatKm(it)} km" },
                                    r.cost?.let { String.format(Locale.US, "%,.0f %s", it, state.currency) },
                                    r.notes,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.deleteRecord(r.id) }) { Icon(Icons.Rounded.Delete, null) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            OutlinedButton(onClick = { logOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mnt_log_service)) }
        }
    }

    if (logOpen) LogServiceDialog(state.odometerKm, { logOpen = false }) { d, k, c, n -> viewModel.logService(id, d, k, c, n) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.mnt_delete_item)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.deleteItem(id); onBack() }) { Text(stringResource(R.string.mnt_delete_item)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.mnt_cancel)) } },
        )
    }
}

