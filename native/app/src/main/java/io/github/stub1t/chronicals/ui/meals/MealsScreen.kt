package io.github.stub1t.chronicals.ui.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Log
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.LocalSnackbar
import io.github.stub1t.chronicals.ui.clock
import io.github.stub1t.chronicals.ui.day.DayViewModel
import io.github.stub1t.chronicals.ui.day.LogSheet
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.typeColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SECTIONS = listOf("breakfast" to R.string.meals_breakfast, "lunch" to R.string.meals_lunch, "dinner" to R.string.meals_dinner, "snack" to R.string.meals_snacks)

/** Snacks also hold drinks (a kind the form no longer offers) and meals without a kind. */
private fun sectionOf(log: Log): String = (log.values["meal"] as? String).takeIf { it == "breakfast" || it == "lunch" || it == "dinner" } ?: "snack"

/**
 * The meals of a day by kind. The tab opens on today; the arrows go back to fill in a missed day.
 * Port of src/routes/meals/+page.svelte.
 */
@Composable
fun MealsScreen(diary: Diary) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val today = Times.today()
    var date by rememberSaveable { mutableStateOf(today.toString()) }
    val day = LocalDate.parse(date)
    val vm: DayViewModel = viewModel(key = "meals-$date") { DayViewModel(diary, day) }
    val state by vm.state.collectAsStateWithLifecycle()
    val mealType = state.types.firstOrNull { it.category == "meal" }
    val color = typeColor(mealType)
    var sheetLog by remember { mutableStateOf<Log?>(null) }
    var sheetPreset by remember { mutableStateOf<Map<String, Any?>?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = day.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.prev_day)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.meals_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                val long = day.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")))
                Text(if (day == today) stringResource(R.string.nav_today) + ", " + long else long, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            IconButton(onClick = { date = day.plusDays(1).toString() }, enabled = day < today) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_day))
            }
        }

        if (mealType != null) {
            val meals = state.entry?.logs.orEmpty().filter { it.entryTypeId == mealType.id }.sortedBy { it.startTime }
            for ((meal, label) in SECTIONS) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    for (log in meals.filter { sectionOf(it) == meal }) {
                        val foods = ((log.values["foods"] as? List<*>)?.map { it.toString() }.orEmpty() + listOfNotNull(log.values["what"] as? String))
                            .filter { it.isNotEmpty() }.joinToString(", ") { localize(it) }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.09f))
                                .clickable { sheetPreset = emptyMap(); sheetLog = log }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(clock(context, log.startTime), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Text(foods.ifEmpty { localize(mealType.name) }.replaceFirstChar { it.titlecase() }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        sheetLog = null; sheetPreset = mapOf("meal" to meal)
                    }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_new))
                    }
                }
            }

            sheetPreset?.let { preset ->
                LogSheet(
                    date = day, type = mealType, log = sheetLog, medications = state.medications, preset = preset,
                    onSave = { input ->
                        val log = sheetLog
                        vm.write {
                            if (log == null) addLog(date, mealType.id, input.startTime, input.endTime, input.values)
                            else updateLog(log.id, input.startTime, input.endTime, input.values)
                        }
                        sheetPreset = null
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.log_saved, localize(mealType.name))) }
                    },
                    onDelete = { sheetLog?.let { log -> vm.write { deleteLog(log.id) } }; sheetPreset = null },
                    onAddOption = { key, option -> vm.write { addOption(mealType, key, option) } },
                    onDismiss = { sheetPreset = null },
                )
            }
        }
    }
}
