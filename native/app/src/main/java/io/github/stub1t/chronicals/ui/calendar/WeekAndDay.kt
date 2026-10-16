package io.github.stub1t.chronicals.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.clock
import io.github.stub1t.chronicals.ui.day.DayContent
import io.github.stub1t.chronicals.ui.day.DayViewModel
import io.github.stub1t.chronicals.ui.hexColor
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.number
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Seven days as a list, each with its logs and triggers. Port of src/routes/calendar/week. */
@Composable
fun WeekList(start: LocalDate, state: RangeState, today: LocalDate, openDay: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0L until 7L) {
            val date = start.plusDays(i)
            val entry = state.entries[date.toString()]
            val logs = entry?.logs.orEmpty().sortedBy { it.startTime }
            val isToday = date == today
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable { openDay(date) }
                    .padding(14.dp)
                    .alpha(if (date > today) 0.55f else 1f),
            ) {
                Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    entry?.wellbeing?.let { Box(Modifier.size(10.dp).background(hexColor(WELLBEING_COLORS.getOrNull(it - 1), MaterialTheme.colorScheme.outline), CircleShape)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (log in logs) Row(verticalAlignment = Alignment.CenterVertically) {
                        val type = state.types[log.entryTypeId]
                        EntryIcon(type, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(localize(type?.name), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(clock(context, log.startTime), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        log.intensity?.let { Text("  ${number(it)}/5", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = hexColor(type?.color, MaterialTheme.colorScheme.primary)) }
                    }
                    if (logs.isEmpty() && isTracked(entry)) Text(stringResource(R.string.calendar_day_no_symptoms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!entry?.triggers.isNullOrEmpty()) Text(entry!!.triggers.joinToString(" · ") { localize(it.name) },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One day from the calendar: the same as Today, for any date. Port of src/routes/calendar/day/[date]. */
@Composable
fun DayPage(diary: Diary, date: LocalDate, openLog: io.github.stub1t.chronicals.ui.OpenLog?, onLogOpened: () -> Unit, onBack: () -> Unit, onDate: (LocalDate) -> Unit, openSettings: () -> Unit = {}) {
    val today = Times.today()
    val vm: DayViewModel = viewModel(key = "day-page-$date") { DayViewModel(diary, date) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_calendar)) }
            Text(stringResource(R.string.nav_calendar), style = MaterialTheme.typography.titleMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            IconButton(onClick = { onDate(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.prev_day)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    date.format(java.time.format.DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy"))),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                )
            }
            IconButton(onClick = { onDate(date.plusDays(1)) }, enabled = date < today) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.next_day))
            }
        }
        DayContent(vm, isToday = date == today, openLogId = openLog?.takeIf { it.date == date }?.logId, onLogOpened = onLogOpened, openSettings = openSettings)
    }
}
