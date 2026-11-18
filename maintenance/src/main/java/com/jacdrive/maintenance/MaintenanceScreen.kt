package com.jacdrive.maintenance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.maintenance.data.DueState
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.MetricReadout
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.theme.JacTheme
import java.util.Locale

@Composable
fun MaintenanceScreen(onBack: (() -> Unit)?, onOpenItem: (Long) -> Unit, viewModel: MaintenanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editOdo by rememberSaveable { mutableStateOf(false) }
    var addCustom by rememberSaveable { mutableStateOf(false) }
    var notifAllowed by rememberSaveable { mutableStateOf(viewModel.notificationsAllowed) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAllowed = it }
    val dueCount = state.items.count { it.status.state == DueState.OVERDUE || it.status.state == DueState.DUE_SOON }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.mnt_title), subtitle = stringResource(R.string.mnt_subtitle), onBack = onBack) }
        item {
            JacCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    MetricReadout(stringResource(R.string.mnt_odometer), state.odometerKm?.let(::formatKm) ?: "—", "km", Modifier.weight(1f))
                    IconButton(onClick = { editOdo = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.mnt_odometer_update)) }
                }
                if (state.odometerKm == null) {
                    Text(stringResource(R.string.mnt_odometer_hint), style = MaterialTheme.typography.bodySmall, color = JacTheme.status.warning)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    val cur = state.currency
                    MetricReadout(stringResource(R.string.mnt_cost_year), String.format(Locale.US, "%,.0f", state.costThisYear), cur, Modifier.weight(1f))
                    MetricReadout(stringResource(R.string.mnt_cost_all), String.format(Locale.US, "%,.0f", state.costAllTime), cur, Modifier.weight(1f))
                }
            }
        }
        if (!notifAllowed && android.os.Build.VERSION.SDK_INT >= 33) {
            item {
                JacCard {
                    Text(stringResource(R.string.mnt_notif_prompt), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.mnt_notif_enable)) }
                }
            }
        }
        item { InfoNote(stringResource(R.string.mnt_manual_note)) }
        item {
            SectionHeader(stringResource(R.string.mnt_items)) {
                if (dueCount > 0) StatusPill(stringResource(R.string.mnt_due_count, dueCount), JacTheme.status.warning)
            }
        }
        items(state.items, key = { it.item.id }) { ui -> ItemRow(ui) { onOpenItem(ui.item.id) } }
        item {
            OutlinedButton(onClick = { addCustom = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mnt_add_custom))
            }
        }
    }

    if (editOdo) OdometerDialog(state.odometerKm, { editOdo = false }, viewModel::updateOdometer)
    if (addCustom) AddCustomDialog({ addCustom = false }, viewModel::addCustom)
}

@Composable
private fun ItemRow(ui: MaintenanceItemUi, onClick: () -> Unit) {
    val s = ui.status
    val color = statusColor(s.state)
    JacCard(onClick = onClick, contentPadding = PaddingValues(14.dp), highlight = if (s.state == DueState.OVERDUE) color else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(ui.builtIn?.icon ?: Icons.Rounded.Build, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(itemName(ui), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    if (s.state == DueState.OVERDUE || s.state == DueState.DUE_SOON) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill(stringResource(if (s.state == DueState.OVERDUE) R.string.mnt_state_overdue else R.string.mnt_state_soon), color)
                    }
                }
                Text(statusText(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.progress?.let { p ->
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { p.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        drawStopIndicator = {},
                    )
                }
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

