package io.github.stub1t.chronicals.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Log
import io.github.stub1t.chronicals.data.Medication
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.duration
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.typeColor
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Symptoms and events last a while; notes and meals are a moment in time. */
fun hasDuration(category: String?) = category == "symptom" || category == "event"

/** Field key of what was taken for a symptom; own entries are kept in the type's extra options under it. */
const val RELIEF_KEY = "relief"
private const val PAINKILLER = "Painkiller"
private val DURATIONS = listOf(15L, 30L, 60L, 120L, 240L)

private sealed interface Length {
    data object Ongoing : Length
    data object Custom : Length
    data class Minutes(val minutes: Long) : Length
}

/** What the sheet saves; the caller writes it to the diary. */
data class LogInput(val startTime: String, val endTime: String?, val values: Map<String, Any?>)

/**
 * Add or edit one log (a headache, say) on a day. Two taps for the common case: the start is now (or
 * noon on past days), the length is "still going" and 1 to 5 scales start in the middle.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSheet(
    date: LocalDate,
    type: EntryType,
    log: Log?,
    medications: List<Medication>,
    preset: Map<String, Any?> = emptyMap(),
    onSave: (LogInput) -> Unit,
    onDelete: () -> Unit,
    onAddOption: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val color = typeColor(type)
    val isToday = date == Times.today()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var time by remember { mutableStateOf(log?.let { Times.local(it.startTime).toLocalTime() } ?: if (isToday) LocalTime.now().truncatedTo(ChronoUnit.MINUTES) else LocalTime.NOON) }
    var length by remember {
        mutableStateOf(
            when {
                log == null -> if (type.category == "event") Length.Minutes(30) else Length.Ongoing
                log.endTime == null -> Length.Ongoing
                else -> Duration.between(Times.parse(log.startTime), Times.parse(log.endTime)).toMinutes()
                    .let { if (it in DURATIONS) Length.Minutes(it) else Length.Custom }
            }
        )
    }
    var endTime by remember { mutableStateOf(log?.endTime?.let { Times.local(it).toLocalTime() } ?: time.plusHours(1)) }
    val values = remember {
        mutableStateMapOf<String, Any?>().apply {
            if (log != null) putAll(log.values)
            else {
                type.fields.filter { it.type == "slider" && it.required }.forEach { put(it.key, Math.round((it.min + it.max) / 2.0).toInt()) }
                putAll(preset)
            }
        }
    }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun start() = date.atTime(time).atZone(Times.zone()).toInstant()
    fun end(): String? = when (val l = length) {
        Length.Ongoing -> null
        is Length.Minutes -> Times.iso(start().plusSeconds(l.minutes * 60))
        // An end before the start means it went on past midnight.
        Length.Custom -> date.atTime(endTime).atZone(Times.zone()).toInstant().let { if (it < start()) it.plusSeconds(86_400) else it }.let(Times::iso)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EntryIcon(type, 52.dp, badge = true)
                    Text(localize(type.name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(stringResource(if (hasDuration(type.category)) R.string.log_started else R.string.log_time))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { picking = "start" }) {
                            Text(io.github.stub1t.chronicals.ui.clock(context, Times.iso(start())), style = MaterialTheme.typography.titleLarge)
                        }
                        if (isToday) FilledTonalButton(onClick = { time = LocalTime.now().truncatedTo(ChronoUnit.MINUTES) }) { Text(stringResource(R.string.log_now)) }
                    }
                }

                if (hasDuration(type.category)) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(stringResource(R.string.log_how_long))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fun pick(l: Length) { length = l; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
                        val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.2f))
                        FilterChip(length == Length.Ongoing, { pick(Length.Ongoing) }, { Text(stringResource(R.string.log_ongoing)) }, colors = chipColors)
                        for (m in DURATIONS) FilterChip(length == Length.Minutes(m), { pick(Length.Minutes(m)) }, { Text(duration(context, m)) }, colors = chipColors)
                        FilterChip(length == Length.Custom, {
                            // Start from the picked length (or an hour) after the start, not the start itself.
                            if (length != Length.Custom) endTime = time.plusMinutes((length as? Length.Minutes)?.minutes ?: 60)
                            pick(Length.Custom)
                        }, { Text(stringResource(R.string.log_until)) }, colors = chipColors)
                    }
                    if (length == Length.Custom) OutlinedButton(onClick = { picking = "end" }) {
                        Text(io.github.stub1t.chronicals.ui.clock(context, Times.iso(date.atTime(endTime).atZone(Times.zone()).toInstant())), style = MaterialTheme.typography.titleLarge)
                    }
                }

                LogFields(type, values, color, onChange = { key, value -> values[key] = value }, onAddOption = onAddOption)

                if (type.category == "symptom") {
                    val relief = (values[RELIEF_KEY] as? List<*>)?.map { it.toString() } ?: emptyList()
                    val options = (medications.filter { it.isActive }.map { it.name } + PAINKILLER + (type.extraOptions[RELIEF_KEY] ?: emptyList()) + relief).distinct()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Label(stringResource(if (type.id == "builtin-allergy") R.string.log_relief else R.string.log_painkillers))
                        Chips(null, options, color, picked = { it in relief },
                            onPick = { values[RELIEF_KEY] = if (it in relief) relief - it else relief + it },
                            onAddOption = { _, typed -> onAddOption(RELIEF_KEY, typed) },
                            onAdded = { if (it !in relief) values[RELIEF_KEY] = relief + it },
                            addLabel = stringResource(R.string.log_relief_placeholder), capitalize = false)
                    }
                }
                Box(Modifier.padding(4.dp))
            }

            Row(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (log != null) TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.delete))
                }
                Button(
                    onClick = {
                        val cleaned = values.filterValues { v -> v != null && v != "" && !(v is List<*> && v.isEmpty()) }
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSave(LogInput(Times.iso(start()), end(), cleaned))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (log != null) stringResource(R.string.save) else stringResource(R.string.log_add, localize(type.name)), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    picking?.let { which ->
        TimeDialog(if (which == "start") time else endTime, onDismiss = { picking = null }) {
            if (which == "start") time = it else endTime = it
            picking = null
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        text = { Text(stringResource(R.string.log_delete_confirm)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, io.github.stub1t.chronicals.ui.is24Hour(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
