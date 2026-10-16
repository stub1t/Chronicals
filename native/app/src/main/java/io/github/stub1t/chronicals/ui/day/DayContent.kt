package io.github.stub1t.chronicals.ui.day

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Log
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.DuoIcon
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.LocalSnackbar
import io.github.stub1t.chronicals.ui.clock
import io.github.stub1t.chronicals.ui.duration
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.typeColor
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.time.Duration
import java.time.Instant

/**
 * Everything about one day: quick logging, the day's entries, triggers and note. Used by Today and the
 * calendar's day page. Meals have their own tab, so they get no tile here. Port of src/lib/diary/DayView.svelte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayContent(vm: DayViewModel, isToday: Boolean) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var sheetType by remember { mutableStateOf<EntryType?>(null) }
    var sheetLog by remember { mutableStateOf<Log?>(null) }

    fun rank(list: List<String>, id: String) = list.indexOf(id).let { if (it == -1) Int.MAX_VALUE else it }
    val quickTypes = state.types.filter { !it.hidden && it.category != "meal" }
        .sortedWith(compareBy({ rank(state.typeRanking, it.id) }, { it.sortOrder }))

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Section(stringResource(R.string.today_log_something), stringResource(R.string.today_log_hint)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
                for (type in quickTypes) Tile(type, Modifier.weight(1f)) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    sheetLog = null; sheetType = type
                }
                // Keeps the last row's tiles the same width as the others.
                repeat((4 - quickTypes.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
            }
        }

        Section(stringResource(if (isToday) R.string.today_logged else R.string.day_entries)) {
            val logs = state.entry?.logs.orEmpty().sortedBy { it.startTime }
            if (logs.isEmpty()) {
                if (state.loaded) Empty(stringResource(R.string.today_nothing_logged))
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (log in logs) {
                    val type = state.types.firstOrNull { it.id == log.entryTypeId }
                    LogRow(log, type, onOpen = { sheetType = type; sheetLog = log }, onEnd = {
                        // Ended now, or at its start if that is still ahead.
                        val now = Instant.now()
                        val start = Times.parse(log.startTime)
                        vm.write { endLog(log.id, Times.iso(if (start > now) start else now)) }
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.log_ended, localize(type?.name))) }
                    })
                }
            }
        }

        Section(
            stringResource(if (isToday) R.string.today_triggers else R.string.day_triggers),
            stringResource(if (isToday) R.string.today_triggers_hint else R.string.day_triggers_hint),
        ) { TriggerChips(state, vm) }

        Section(stringResource(R.string.today_note)) { NoteField(vm, state.entry?.note, state.loaded) }
    }

    sheetType?.let { type ->
        LogSheet(
            date = vm.date,
            type = state.types.firstOrNull { it.id == type.id } ?: type,
            log = sheetLog,
            medications = state.medications,
            onSave = { input ->
                val log = sheetLog
                vm.write {
                    if (log == null) addLog(vm.date.toString(), type.id, input.startTime, input.endTime, input.values)
                    else updateLog(log.id, input.startTime, input.endTime, input.values)
                }
                sheetType = null
                scope.launch { snackbar.showSnackbar(context.getString(R.string.log_saved, localize(type.name))) }
            },
            onDelete = {
                sheetLog?.let { log -> vm.write { deleteLog(log.id) } }
                sheetType = null
                scope.launch { snackbar.showSnackbar(context.getString(R.string.log_deleted)) }
            },
            onAddOption = { key, option ->
                val current = state.types.firstOrNull { it.id == type.id } ?: type
                vm.write { addOption(current, key, option) }
            },
            onDismiss = { sheetType = null },
        )
    }
}

@Composable
fun Section(title: String, hint: String? = null, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            trailing()
        }
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Tile(type: EntryType, modifier: Modifier, onClick: () -> Unit) {
    val color = typeColor(type)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier.height(96.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(4.dp)) {
            EntryIcon(type, 40.dp, badge = true)
            Spacer(Modifier.height(8.dp))
            Text(localize(type.name), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Empty(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 24.dp)) {
            Box(Modifier.size(44.dp).background(Color(0xFF3CBF88).copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(io.github.stub1t.chronicals.ui.SproutIcon, null, tint = Color(0xFF2FAE78), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** One log as a tappable card; an ongoing symptom gets "End now". Port of src/lib/diary/LogList.svelte. */
@Composable
fun LogRow(log: Log, type: EntryType?, onOpen: () -> Unit, onEnd: () -> Unit) {
    val context = LocalContext.current
    val color = typeColor(type)
    val meal = type?.category == "meal"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.09f))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            EntryIcon(type, 42.dp, badge = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // A meal shows its kind ("Lunch") as the name and its foods below.
                val name = localize(((log.values["meal"] as? String)?.takeIf { meal }) ?: type?.name)
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val start = clock(context, log.startTime)
                val meta = log.endTime?.let {
                    "$start – ${clock(context, it)} · " + duration(context, Duration.between(Times.parse(log.startTime), Times.parse(it)).toMinutes())
                } ?: start
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (meal) {
                    val foods = ((log.values["foods"] as? List<*>)?.map { it.toString() }.orEmpty() + listOfNotNull(log.values["what"] as? String))
                        .filter { it.isNotEmpty() }.joinToString(", ") { localize(it) }
                    if (foods.isNotEmpty()) Text(foods, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val relief = (log.values[RELIEF_KEY] as? List<*>)?.map { localize(it.toString()) }.orEmpty()
                if (relief.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Medication, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" " + relief.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            log.intensity?.let { level ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 1..5) Box(Modifier.size(7.dp).background(if (level >= i) color else color.copy(alpha = 0.25f), CircleShape))
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (log.endTime == null && hasDuration(type?.category)) {
            HorizontalDivider(color = color.copy(alpha = 0.2f))
            // After three hours, ask whether it is still there.
            val minutes = Duration.between(Times.parse(log.startTime), Instant.now()).toMinutes().coerceAtLeast(0)
            val overdue = minutes >= 180
            Row(Modifier.fillMaxWidth().clickable(onClick = onEnd).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.StopCircle, null, Modifier.size(18.dp), tint = color)
                Spacer(Modifier.width(8.dp))
                var text = stringResource(R.string.log_ongoing)
                if (minutes >= 1) text += " · " + stringResource(R.string.log_ongoing_since, duration(context, minutes, roundDown = true))
                if (overdue) text += " · " + stringResource(R.string.log_still_there)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f), fontWeight = if (overdue) FontWeight.Bold else null)
                Text(stringResource(R.string.log_end_now), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The day's note; saved 0.7 s after the last key press. */
@Composable
private fun NoteField(vm: DayViewModel, stored: String?, loaded: Boolean) {
    // Read once when loaded: syncing from later saves could overwrite what is being typed.
    var note by rememberSaveable(loaded) { mutableStateOf(stored ?: "") }
    var edited by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(note, edited) {
        if (!edited) return@LaunchedEffect
        saved = false
        delay(700)
        vm.write { setNote(vm.date.toString(), note) }
        saved = true
    }
    OutlinedTextField(
        value = note,
        onValueChange = { note = it; edited = true },
        placeholder = { Text(stringResource(R.string.today_note_placeholder)) },
        minLines = 3,
        supportingText = if (saved) ({ Text(stringResource(R.string.today_note_saved)) }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One-tap triggers: the most used ones as chips, everything else and a field for a new one in a
 * sheet. Port of src/lib/diary/TriggerChips.svelte.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TriggerChips(state: DayState, vm: DayViewModel, max: Int = 8) {
    val haptics = LocalHapticFeedback.current
    var showAll by remember { mutableStateOf(false) }
    // Deleted triggers only show on days that have them.
    val offered = state.triggers.filter { !it.hidden || state.hasTrigger(it.id) }
    val byId = offered.associateBy { it.id }
    val quick = (state.triggerRanking.mapNotNull { byId[it] } +
        offered.filter { state.hasTrigger(it.id) && it.id !in state.triggerRanking } +
        offered.filter { it.id !in state.triggerRanking }).distinctBy { it.id }.take(max)

    fun toggle(id: String) {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        val on = state.hasTrigger(id)
        vm.write { if (on) removeDayTrigger(vm.date.toString(), id) else addDayTrigger(vm.date.toString(), id) }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (trigger in quick) FilterChip(state.hasTrigger(trigger.id), { toggle(trigger.id) }, { Text(localize(trigger.name)) })
        FilterChip(false, { showAll = true }, { Text(stringResource(R.string.add_new)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) })
    }

    if (showAll) ModalBottomSheet(onDismissRequest = { showAll = false }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.triggers_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            for (category in listOf("activity", "food", "environment", "medication", "custom")) {
                val inGroup = offered.filter { it.category == category }.sortedBy { localize(it.name) }
                if (inGroup.isEmpty()) continue
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(stringResource(categoryLabel(category)))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (trigger in inGroup) FilterChip(state.hasTrigger(trigger.id), { toggle(trigger.id) }, { Text(localize(trigger.name)) })
                    }
                }
            }
            var name by remember { mutableStateOf("") }
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 120) name = it },
                placeholder = { Text(stringResource(R.string.triggers_new_placeholder)) },
                label = { Text(stringResource(R.string.triggers_new)) },
                singleLine = true,
                trailingIcon = {
                    androidx.compose.material3.TextButton(onClick = {
                        val typed = name.trim()
                        name = ""
                        if (typed.isEmpty()) return@TextButton
                        // An existing trigger, also under its translated name, is picked instead of added twice.
                        val existing = state.triggers.firstOrNull { it.name.equals(typed, true) || localize(it.name).equals(typed, true) }
                        if (existing != null && !existing.hidden) {
                            if (!state.hasTrigger(existing.id)) toggle(existing.id)
                        } else vm.write {
                            createTrigger(existing?.name ?: typed, existing?.category ?: "custom")?.let { addDayTrigger(vm.date.toString(), it.id) }
                        }
                    }) { Text(stringResource(R.string.triggers_add)) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun categoryLabel(category: String): Int = when (category) {
    "activity" -> R.string.trigger_activity
    "food" -> R.string.trigger_food
    "environment" -> R.string.trigger_environment
    "medication" -> R.string.trigger_medication
    else -> R.string.trigger_custom
}
