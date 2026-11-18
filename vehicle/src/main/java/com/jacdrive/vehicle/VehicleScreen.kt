package com.jacdrive.vehicle

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.database.VehicleProfileEntity
import com.jacdrive.ui.components.InDevelopmentChip
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.theme.Readout
import java.text.NumberFormat
import java.util.Locale

@Composable
fun VehicleScreen(
    onBack: (() -> Unit)?,
    onOpenMaintenance: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.vehicle_title),
                onBack = onBack,
                actions = {
                    if (!state.editing && state.profile != null) {
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.vehicle_edit))
                        }
                    }
                },
            )
        }
        val profile = state.profile
        if (profile != null) {
            item { VehicleHero(profile) }
            if (state.editing) {
                item { VehicleEditForm(state.form, viewModel::updateForm, viewModel::save, viewModel::cancelEditing) }
            } else {
                item { VehicleDetails(profile) }
            }
            item { SectionHeader(stringResource(R.string.vehicle_adapter)) }
            item {
                JacCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bluetooth, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                profile.adapterName ?: stringResource(R.string.vehicle_adapter_none),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                profile.adapterAddress ?: stringResource(R.string.vehicle_adapter_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InDevelopmentChip()
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.vehicle_records)) }
            item { LinkRow(Icons.Rounded.Build, stringResource(R.string.vehicle_maintenance_records), onOpenMaintenance) }
            item { LinkRow(Icons.AutoMirrored.Rounded.ManageSearch, stringResource(R.string.vehicle_diagnostic_history), onOpenDiagnostics) }
            item {
                Text(
                    stringResource(R.string.vehicle_spec_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun VehicleHero(p: VehicleProfileEntity) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.30f), MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainerLowest),
                ),
            )
            .padding(22.dp),
    ) {
        Column {
            Text(p.make.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent)
            Text(p.name, style = MaterialTheme.typography.displaySmall)
            Text("${p.modelYear} · ${p.trim}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.vehicle_odometer).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(NumberFormat.getIntegerInstance(Locale.US).format(p.currentMileageKm), style = Readout.Large)
                Spacer(Modifier.width(6.dp))
                Text("km", style = Readout.Unit, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun VehicleDetails(p: VehicleProfileEntity) {
    val notSet = stringResource(R.string.vehicle_not_set)
    JacCard {
        DetailRow(stringResource(R.string.vehicle_model), "${p.make} ${p.model}")
        DetailRow(stringResource(R.string.vehicle_year), p.modelYear.toString())
        DetailRow(stringResource(R.string.vehicle_trim), p.trim)
        DetailRow(stringResource(R.string.vehicle_fuel), fuelLabel(runCatching { FuelType.valueOf(p.fuelType) }.getOrDefault(FuelType.PETROL)))
        DetailRow(stringResource(R.string.vehicle_engine), p.engineDescription ?: notSet)
        DetailRow(stringResource(R.string.vehicle_transmission), p.transmission ?: notSet)
        DetailRow(stringResource(R.string.vehicle_vin), p.vin ?: notSet, last = true)
    }
}

@Composable
private fun DetailRow(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun fuelLabel(type: FuelType): String = stringResource(
    when (type) {
        FuelType.PETROL -> R.string.fuel_petrol
        FuelType.DIESEL -> R.string.fuel_diesel
        FuelType.HYBRID -> R.string.fuel_hybrid
        FuelType.ELECTRIC -> R.string.fuel_electric
    },
)

@Composable
private fun VehicleEditForm(
    form: VehicleForm,
    onChange: ((VehicleForm) -> VehicleForm) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    JacCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(form.name, { v -> onChange { it.copy(name = v) } }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.vehicle_name)) }, singleLine = true, isError = form.name.isBlank())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(form.modelYear, { v -> onChange { it.copy(modelYear = v.filter(Char::isDigit).take(4)) } },
                    Modifier.weight(1f), label = { Text(stringResource(R.string.vehicle_year)) }, singleLine = true,
                    isError = form.yearError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(form.trim, { v -> onChange { it.copy(trim = v) } }, Modifier.weight(1f),
                    label = { Text(stringResource(R.string.vehicle_trim)) }, singleLine = true)
            }
            OutlinedTextField(form.mileageKm, { v -> onChange { it.copy(mileageKm = v.filter(Char::isDigit).take(7)) } },
                Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.vehicle_mileage)) }, singleLine = true,
                isError = form.mileageError, suffix = { Text("km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(form.vin, { v -> onChange { it.copy(vin = v.uppercase().take(17)) } }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.vehicle_vin_optional)) }, singleLine = true, isError = form.vinError,
                supportingText = { if (form.vinError) Text(stringResource(R.string.vehicle_vin_error)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            Text(stringResource(R.string.vehicle_fuel), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FuelType.entries.forEach { t ->
                    FilterChip(selected = form.fuelType == t, onClick = { onChange { it.copy(fuelType = t) } }, label = { Text(fuelLabel(t)) })
                }
            }
            OutlinedTextField(form.engine, { v -> onChange { it.copy(engine = v) } }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.vehicle_engine)) }, placeholder = { Text(stringResource(R.string.vehicle_engine_hint)) })
            OutlinedTextField(form.transmission, { v -> onChange { it.copy(transmission = v) } }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.vehicle_transmission)) }, singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onSave, enabled = form.isValid) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    JacCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
