package com.jacdrive.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout

@Composable
fun DtcDetailScreen(code: String, onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val info = remember(code) { viewModel.info(code) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ar = isArabic()
    val color = severityColor(info.severity)
    val occurrences = state.sessions.filter { code in it.allCodes }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(info.code, subtitle = systemLabel(info.system), onBack = onBack) }
        item {
            JacCard(highlight = color) {
                Text(info.code, style = Readout.Large, color = color)
                Text(info.title.get(ar), style = MaterialTheme.typography.titleLarge)
                Text(info.title.get(!ar), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(severityLabel(info.severity), color)
                    if (info.manufacturerSpecific) StatusPill(stringResource(R.string.dtc_manufacturer), JacTheme.status.info)
                }
                Spacer(Modifier.height(8.dp))
                Text(severityAdvice(info.severity), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (info.causes.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.dtc_causes)) }
            item {
                JacCard {
                    info.causes.forEach { c ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Text("•", color = color)
                            Spacer(Modifier.width(10.dp))
                            Text(c.get(ar), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.dtc_steps)) }
        item {
            JacCard {
                info.steps.forEachIndexed { i, s ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${i + 1}.", style = Readout.Small, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(s.get(ar), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.dtc_history)) }
        item {
            JacCard {
                if (occurrences.isEmpty()) {
                    Text(stringResource(R.string.dtc_never_seen), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.dtc_seen_count, occurrences.size), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.dtc_seen_range, formatDate(occurrences.last().timestampMs), formatDate(occurrences.first().timestampMs)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            InfoNote(stringResource(if (info.known) R.string.dtc_disclaimer else R.string.dtc_unknown_disclaimer))
        }
    }
}
