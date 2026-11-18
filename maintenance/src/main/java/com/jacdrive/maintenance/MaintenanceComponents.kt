package com.jacdrive.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jacdrive.maintenance.data.DueState
import com.jacdrive.maintenance.data.ItemStatus
import com.jacdrive.ui.theme.JacTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun itemName(ui: MaintenanceItemUi): String = ui.builtIn?.let { stringResource(it.title) } ?: ui.item.customName.orEmpty()

@Composable
fun formatDay(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, LocalConfiguration.current.locales[0]).format(Date(ms))

fun formatKm(km: Int): String = NumberFormat.getIntegerInstance(java.util.Locale.US).format(km)

@Composable
fun statusColor(state: DueState) = when (state) {
    DueState.OVERDUE -> JacTheme.status.critical
    DueState.DUE_SOON -> JacTheme.status.warning
    DueState.OK -> JacTheme.status.ok
    else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun statusText(s: ItemStatus): String = when (s.state) {
    DueState.NOT_CONFIGURED -> stringResource(R.string.mnt_interval_not_set)
    DueState.NO_BASELINE -> stringResource(R.string.mnt_no_baseline)
    DueState.OVERDUE -> listOfNotNull(
        s.remainingKm?.takeIf { it <= 0 }?.let { stringResource(R.string.mnt_overdue_km, formatKm(-it)) },
        s.remainingDays?.takeIf { it <= 0 }?.let { stringResource(R.string.mnt_overdue_days, -it) },
    ).joinToString(" · ").ifEmpty { stringResource(R.string.mnt_state_overdue) }
    else -> listOfNotNull(
        s.remainingKm?.let { stringResource(R.string.mnt_in_km, formatKm(it)) },
        s.remainingDays?.let { stringResource(R.string.mnt_in_days, it) },
    ).joinToString(" · ")
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }.take(9)) },
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Date chooser shown as a read-only field that opens the Material date picker. */
@Composable
fun DateField(valueMs: Long?, onChange: (Long?) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valueMs?.let { formatDay(it) } ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { TextButton(onClick = { open = true }) { Text(stringResource(R.string.mnt_pick)) } },
        modifier = modifier,
    )
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = valueMs ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange(picker.selectedDateMillis); open = false }) { Text(stringResource(R.string.mnt_ok)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.mnt_cancel)) } },
        ) { DatePicker(picker) }
    }
}

@Composable
fun AddCustomDialog(onDismiss: () -> Unit, onAdd: (String, Int?, Int?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var km by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mnt_add_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.mnt_item_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField(km, { km = it }, stringResource(R.string.mnt_interval_km), Modifier.fillMaxWidth(), "km")
                NumberField(months, { months = it }, stringResource(R.string.mnt_interval_months), Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, km.toIntOrNull(), months.toIntOrNull()); onDismiss() }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.mnt_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mnt_cancel)) } },
    )
}

@Composable
fun OdometerDialog(current: Int?, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var km by rememberSaveable { mutableStateOf(current?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mnt_odometer_update)) },
        text = { NumberField(km, { km = it }, stringResource(R.string.mnt_odometer), Modifier.fillMaxWidth(), "km") },
        confirmButton = { TextButton(onClick = { km.toIntOrNull()?.let(onSave); onDismiss() }, enabled = km.toIntOrNull() != null) { Text(stringResource(R.string.mnt_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mnt_cancel)) } },
    )
}

@Composable
fun LogServiceDialog(odometer: Int?, onDismiss: () -> Unit, onLog: (Long, Int?, Double?, String?) -> Unit) {
    var date by rememberSaveable { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var km by rememberSaveable { mutableStateOf(odometer?.toString() ?: "") }
    var cost by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mnt_log_service)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(date, { date = it }, stringResource(R.string.mnt_date), Modifier.fillMaxWidth())
                NumberField(km, { km = it }, stringResource(R.string.mnt_odometer), Modifier.fillMaxWidth(), "km")
                NumberField(cost, { cost = it }, stringResource(R.string.mnt_cost), Modifier.fillMaxWidth(), decimal = true)
                OutlinedTextField(notes, { notes = it.take(200) }, label = { Text(stringResource(R.string.mnt_notes)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onLog(date ?: System.currentTimeMillis(), km.toIntOrNull(), cost.toDoubleOrNull(), notes); onDismiss() }, enabled = date != null) {
                Text(stringResource(R.string.mnt_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mnt_cancel)) } },
    )
}
