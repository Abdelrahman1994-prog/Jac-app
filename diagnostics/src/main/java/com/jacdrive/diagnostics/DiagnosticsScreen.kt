package com.jacdrive.diagnostics

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.DataMode
import com.jacdrive.ui.components.DataModeBanner
import com.jacdrive.ui.components.EmptyState
import com.jacdrive.ui.components.InDevelopmentChip
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader

@Composable
fun DiagnosticsScreen(
    onBack: (() -> Unit)?,
    onOpenHealth: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val live = state.snapshot.mode == DataMode.LIVE

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.diag_title),
                subtitle = stringResource(R.string.diag_subtitle),
                onBack = onBack,
                actions = { InDevelopmentChip() },
            )
        }
        item { DataModeBanner(state.snapshot.mode) }
        item { MilCard(live) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Radar, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diag_scan))
                }
                OutlinedButton(
                    onClick = {}, enabled = false,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diag_clear))
                }
            }
        }
        item {
            Text(
                stringResource(if (live) R.string.diag_scan_ready else R.string.diag_scan_requires_adapter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SectionHeader(stringResource(R.string.diag_codes)) }
        item { CodeGroup(stringResource(R.string.diag_stored), stringResource(R.string.diag_stored_sub)) }
        item { CodeGroup(stringResource(R.string.diag_pending), stringResource(R.string.diag_pending_sub)) }
        item { CodeGroup(stringResource(R.string.diag_permanent), stringResource(R.string.diag_permanent_sub)) }

        item { SectionHeader(stringResource(R.string.diag_health)) }
        item {
            JacCard(onClick = onOpenHealth) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MonitorHeart, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.diag_health_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.diag_history)) }
        item {
            JacCard {
                EmptyState(Icons.Rounded.History, stringResource(R.string.diag_history_empty), stringResource(R.string.diag_history_empty_sub))
            }
        }
        item { InfoNote(stringResource(R.string.diag_scope_note)) }
    }
}

@Composable
private fun MilCard(live: Boolean) {
    JacCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diag_mil), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.diag_not_read), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (live) R.string.diag_mil_hint_live else R.string.diag_mil_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodeGroup(title: String, subtitle: String) {
    JacCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(R.string.diag_not_read), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(0.dp).fillMaxWidth())
}
