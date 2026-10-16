package io.github.stub1t.chronicals.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Entry
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.clock
import io.github.stub1t.chronicals.ui.hexColor
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.number
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun longDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM")))

fun monthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "LLLLyyyy")))

/** Month and week views with search. Port of src/routes/calendar. */
@Composable
fun CalendarScreen(diary: Diary, openDay: (LocalDate) -> Unit, openSearch: () -> Unit) {
    val today = Times.today()
    var week by rememberSaveable { mutableStateOf(false) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var weekStart by rememberSaveable { mutableStateOf(today.with(DayOfWeek.MONDAY).toString()) }
    var selected by rememberSaveable { mutableStateOf<String?>(today.toString()) }
    val shownMonth = YearMonth.parse(month)
    val shownWeek = LocalDate.parse(weekStart)
    val range = if (week) shownWeek to shownWeek.plusDays(6) else RangeViewModel.month(shownMonth)
    val vm: RangeViewModel = viewModel(key = "calendar") { RangeViewModel(diary, range) }
    LaunchedEffect(range) { vm.range.value = range }
    val state by vm.state.collectAsStateWithLifecycle()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun step(by: Long) {
        if (week) weekStart = shownWeek.plusWeeks(by).toString() else month = shownMonth.plusMonths(by).toString()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                SegmentedButton(!week, { week = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.month_view)) }
                SegmentedButton(week, { week = true; weekStart = (selected?.let(LocalDate::parse) ?: today).with(DayOfWeek.MONDAY).toString() },
                    SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.week_view)) }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = openSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.search_open)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { step(-1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(if (week) R.string.prev_week else R.string.prev_month)) }
            Text(
                if (week) weekTitle(shownWeek) else monthTitle(shownMonth),
                Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            )
            IconButton(onClick = { step(1) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(if (week) R.string.next_week else R.string.next_month)) }
        }
        val away = if (week) today !in shownWeek..shownWeek.plusDays(6) else YearMonth.from(today) != shownMonth
        if (away) TextButton(onClick = {
            month = YearMonth.from(today).toString(); weekStart = today.with(DayOfWeek.MONDAY).toString(); selected = today.toString()
        }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.calendar_back_to_today)) }

        if (week) WeekList(shownWeek, state, today, openDay)
        else {
            // Swiping is a shortcut; the arrows do the same.
            Box(Modifier.pointerInput(month) {
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                    if (kotlin.math.abs(dx) > 120) step(if ((dx < 0) != rtl) 1 else -1)
                }) { _, amount -> dx += amount }
            }) {
                MonthGrid(shownMonth, state, today, selected?.let(LocalDate::parse)) { selected = it.toString() }
            }
            selected?.let(LocalDate::parse)?.takeIf { YearMonth.from(it) == shownMonth }?.let { day ->
                DaySummary(day, state.entries[day.toString()], state.types) { openDay(day) }
            }
        }
    }
}

private fun weekTitle(start: LocalDate): String {
    val end = start.plusDays(6)
    val f = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMM"))
    return "${start.format(f)} – ${end.format(f)}"
}

/** A month of soft day cells, tinted by the strongest symptom, with a dot per entry type. */
@Composable
private fun MonthGrid(month: YearMonth, state: RangeState, today: LocalDate, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            for (d in DayOfWeek.entries) Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val blanks = month.atDay(1).dayOfWeek.value - 1
        val cells = List(blanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        for (row in cells.chunked(7)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 7) {
                val day = row.getOrNull(i)
                if (day == null) { Spacer(Modifier.weight(1f)); continue }
                val entry = state.entries[day.toString()]
                val logs = entry?.logs.orEmpty()
                val worst = logs.filter { state.types[it.entryTypeId]?.category == "symptom" }.maxByOrNull { it.intensity ?: 3.0 }
                val tint = worst?.let { state.types[it.entryTypeId]?.color } ?: entry?.wellbeing?.let { WELLBEING_COLORS.getOrNull(it - 1) }
                val strength = worst?.let { 0.12f + (it.intensity ?: 3.0).toFloat() * 0.07f } ?: 0.16f
                // Meals would put a dot on nearly every day.
                val dots = logs.mapNotNull { state.types[it.entryTypeId] }.filter { it.category != "meal" }.mapNotNull { it.color }.distinct().take(3)
                val isSelected = day == selected
                Surface(
                    onClick = { onSelect(day) },
                    shape = RoundedCornerShape(12.dp),
                    color = tint?.let { hexColor(it, surface).copy(alpha = strength).compositeOver(surface) } ?: surface,
                    border = when {
                        isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else -> null
                    },
                    modifier = Modifier.weight(1f).aspectRatio(0.9f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val isToday = day == today
                        Box(
                            Modifier.size(28.dp).then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            for (c in dots) Box(Modifier.size(5.dp).background(hexColor(c, surface), CircleShape))
                        }
                    }
                }
            }
        }
    }
}

/** A day at a glance under the month: entries, triggers, note, and a way in. */
@Composable
fun DaySummary(date: LocalDate, entry: Entry?, types: Map<String, EntryType>, onOpen: () -> Unit) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(longDate(date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                entry?.wellbeing?.let { Box(Modifier.size(16.dp).background(hexColor(WELLBEING_COLORS.getOrNull(it - 1), MaterialTheme.colorScheme.outline), CircleShape)) }
            }
            val logs = entry?.logs.orEmpty().sortedBy { it.startTime }
            if (logs.isEmpty()) Text(
                stringResource(if (isTracked(entry)) R.string.calendar_day_no_symptoms else R.string.calendar_day_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (log in logs) Row(verticalAlignment = Alignment.CenterVertically) {
                val type = types[log.entryTypeId]
                EntryIcon(type, 30.dp, badge = true)
                Spacer(Modifier.width(10.dp))
                Text(localize(type?.name), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(clock(context, log.startTime) + (log.endTime?.let { " – " + clock(context, it) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                log.intensity?.let { Text("  ${number(it)}/5", style = MaterialTheme.typography.labelLarge, color = hexColor(type?.color, MaterialTheme.colorScheme.primary), fontWeight = FontWeight.Bold) }
            }
            if (!entry?.triggers.isNullOrEmpty()) Text(entry!!.triggers.joinToString(" · ") { localize(it.name) },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (isTracked(entry)) R.string.calendar_open_day else R.string.calendar_add_to_day))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}
