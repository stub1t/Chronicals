package io.github.stub1t.chronicals.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Medication
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.LocalSnackbar
import io.github.stub1t.chronicals.ui.day.Label
import io.github.stub1t.chronicals.ui.day.TimeDialog
import io.github.stub1t.chronicals.ui.localize
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Pink = Color(0xFFE0679A)

private fun dose(med: Medication) = med.dosage?.takeIf { it.isNotEmpty() }?.let { if (med.unit.isNullOrEmpty()) it else "$it ${med.unit}" } ?: ""
private fun date(d: String) = LocalDate.parse(d).format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMyyyy")))

/** Active and past medications, dose changes and the change history. Port of src/routes/me/medications. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationsPage(vm: MeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Medication?>(null) }
    var editOpen by remember { mutableStateOf(false) }
    var doseMed by remember { mutableStateOf<Medication?>(null) }
    var showPast by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Medication?>(null) }
    val today = Times.today().toString()
    val active = state.medications.filter { it.isActive }
    val past = state.medications.filter { !it.isActive }

    SubPage(stringResource(R.string.medications_title), onBack, action = {
        FilledTonalButton(onClick = { editing = null; editOpen = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.add_new)) }
    }) {
        if (active.isEmpty()) FormCard {
            Text(stringResource(R.string.medications_empty_active), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Hint(stringResource(R.string.medications_empty_hint))
        }
        for (med in active) FormCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Pink.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Medication, null, tint = Pink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(med.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Hint(stringResource(R.string.medications_since, date(med.startDate)))
                }
                if (!med.dosage.isNullOrEmpty()) Text(dose(med), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp))
            }
            if (med.reminderTimes.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notifications, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text(med.reminderTimes.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { doseMed = med }) { Text(stringResource(R.string.medications_change_dose)) }
                OutlinedButton(onClick = { editing = med; editOpen = true }) { Text(stringResource(R.string.medications_edit)) }
                TextButton(onClick = { vm.write { addMedicationChange(med.id, "stopped", today) } }) { Text(stringResource(R.string.medications_stop)) }
            }
        }
        if (past.isNotEmpty()) Column {
            TextButton(onClick = { showPast = !showPast }) {
                Text(stringResource(R.string.medications_past, past.size.toString()))
                Icon(if (showPast) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null)
            }
            if (showPast) for (med in past) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(med.name + (if (!med.dosage.isNullOrEmpty()) "  " + dose(med) else ""), fontWeight = FontWeight.SemiBold)
                    Hint(date(med.startDate) + (med.endDate?.let { " → " + date(it) } ?: ""))
                }
                OutlinedButton(onClick = { vm.write { addMedicationChange(med.id, "started", today) } }) { Text(stringResource(R.string.medications_restart)) }
                IconButton(onClick = { confirmDelete = med }) { Icon(Icons.Outlined.Close, stringResource(R.string.triggers_delete, med.name)) }
            }
        }
        if (state.changes.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.medications_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            FormCard {
                for (change in state.changes) Row(verticalAlignment = Alignment.Top) {
                    val dot = when (change.changeType) { "started" -> Color(0xFF2FAE78); "stopped" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
                    Box(Modifier.padding(top = 6.dp).size(10.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        val med = state.medications.firstOrNull { it.id == change.medicationId }
                        val kind = stringResource(when (change.changeType) { "started" -> R.string.medications_change_started; "stopped" -> R.string.medications_change_stopped; else -> R.string.medications_change_dosage })
                        Text((med?.name ?: stringResource(R.string.medications_deleted)) + " · " + kind, fontWeight = FontWeight.SemiBold)
                        if (change.oldDosage != null || change.newDosage != null) Text(listOfNotNull(change.oldDosage, change.newDosage).joinToString(" → "), style = MaterialTheme.typography.bodyMedium)
                        change.reason?.let { Hint(it) }
                    }
                    Hint(date(change.changeDate))
                }
            }
        }
    }

    if (editOpen) MedicationSheet(vm, editing) { editOpen = false }
    doseMed?.let { med -> DoseSheet(vm, med) { doseMed = null } }
    confirmDelete?.let { med ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, text = { Text(stringResource(R.string.medications_delete_confirm, med.name)) },
            confirmButton = { TextButton(onClick = { confirmDelete = null; vm.write { deleteMedication(med.id) } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MedicationSheet(vm: MeViewModel, editing: Medication?, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var dosage by remember { mutableStateOf(editing?.dosage ?: "") }
    var unit by remember { mutableStateOf(editing?.unit ?: "mg") }
    var start by remember { mutableStateOf(editing?.startDate ?: Times.today().toString()) }
    var reminders by remember { mutableStateOf(editing?.reminderTimes ?: emptyList()) }
    var forTypes by remember { mutableStateOf(editing?.forTypes ?: emptyList()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val symptomTypes = state.types.filter { it.category == "symptom" || it.category == "event" }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(if (editing != null) R.string.medications_edit_title else R.string.medications_add_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.medications_name)) }, placeholder = { Text(stringResource(R.string.medications_name_placeholder)) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(dosage, { if (it.length <= 64) dosage = it }, Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.medications_dosage)) }, placeholder = { Text(stringResource(R.string.medications_dosage_placeholder)) })
                OutlinedTextField(unit, { if (it.length <= 32) unit = it }, Modifier.weight(1f), singleLine = true,
                    label = { Text(stringResource(R.string.medications_unit)) }, placeholder = { Text(stringResource(R.string.medications_unit_placeholder)) })
            }
            if (symptomTypes.isNotEmpty()) {
                Label(stringResource(R.string.medications_for))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in symptomTypes) FilterChip(t.id in forTypes, { forTypes = if (t.id in forTypes) forTypes - t.id else forTypes + t.id },
                        { Text(localize(t.name)) }, leadingIcon = { EntryIcon(t, 18.dp) })
                }
                Hint(stringResource(R.string.medications_for_hint))
            }
            Label(stringResource(R.string.medications_start_date))
            OutlinedButton(onClick = { pickDate = true }) { Text(date(start)) }
            Label(stringResource(R.string.medications_reminders))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (time in reminders) InputChip(false, { reminders = reminders - time }, { Text(time) },
                    trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.medications_remove_reminder), Modifier.size(14.dp)) })
                InputChip(false, { pickTime = true }, { Text(stringResource(R.string.medications_add_reminder)) }, leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                Button(modifier = Modifier.weight(1f), onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) { scope.launch { snackbar.showSnackbar(context.getString(R.string.medications_name_required)) }; return@Button }
                    val d = dosage.trim().ifEmpty { null }; val u = unit.trim().ifEmpty { null }
                    val times = reminders.sorted()
                    vm.write {
                        if (editing != null) updateMedication(editing.id, n, d, u, start, times, forTypes)
                        else createMedication(n, d, u, start, times, forTypes)
                    }
                    onClose()
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.log_saved, n)) }
                }) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (pickDate) DateDialog(start, onDismiss = { pickDate = false }) { start = it; pickDate = false }
    if (pickTime) TimeDialog(LocalTime.of(8, 0), onDismiss = { pickTime = false }) {
        pickTime = false
        val t = "%02d:%02d".format(it.hour, it.minute)
        if (t !in reminders) reminders = reminders + t
    }
    if (confirmDelete && editing != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        text = { Text(stringResource(R.string.medications_delete_confirm, editing.name)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onClose(); vm.write { deleteMedication(editing.id) } }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

/** A new dose; logged as a change, and the medication shows it from now on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseSheet(vm: MeViewModel, med: Medication, onClose: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var newDose by remember { mutableStateOf("") }
    var on by remember { mutableStateOf(Times.today().toString()) }
    var reason by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.medications_dose_title, med.name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!med.dosage.isNullOrEmpty()) Hint(stringResource(R.string.medications_dose_current, dose(med)))
            OutlinedTextField(newDose, { if (it.length <= 64) newDose = it }, Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), label = { Text(stringResource(R.string.medications_new_dosage)) },
                suffix = { med.unit?.let { Text(it) } })
            Label(stringResource(R.string.medications_dose_date))
            OutlinedButton(onClick = { pickDate = true }) { Text(date(on)) }
            OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.medications_reason)) },
                placeholder = { Text(stringResource(R.string.medications_reason_placeholder)) })
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val value = newDose.trim()
                if (value.isEmpty()) return@Button
                val unit = med.unit?.let { " $it" } ?: ""
                vm.write {
                    addMedicationChange(med.id, "dosage_change", on, med.dosage?.takeIf { it.isNotEmpty() }?.let { "$it$unit" }, "$value$unit", reason.trim().ifEmpty { null })
                    setDosage(med.id, value)
                }
                onClose()
                scope.launch { snackbar.showSnackbar(context.getString(R.string.medications_dose_saved, med.name, "$value$unit")) }
            }) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (pickDate) DateDialog(on, onDismiss = { pickDate = false }) { on = it; pickDate = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberDatePickerState(LocalDate.parse(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) } }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
}
