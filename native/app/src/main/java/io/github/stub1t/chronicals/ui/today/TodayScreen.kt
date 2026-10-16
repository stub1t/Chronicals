package io.github.stub1t.chronicals.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.day.DayContent
import io.github.stub1t.chronicals.ui.day.DayViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(diary: Diary, openLog: io.github.stub1t.chronicals.ui.OpenLog? = null, onLogOpened: () -> Unit = {}, openSettings: () -> Unit = {}) {
    // Back from the background after midnight: Today moves on to the new day.
    var today by remember { mutableStateOf(Times.today()) }
    LifecycleResumeEffect(Unit) {
        today = Times.today()
        onPauseOrDispose {}
    }
    val vm: DayViewModel = viewModel(key = "day-$today") { DayViewModel(diary, today) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 32.dp)) {
        Text(
            today.format(DateTimeFormatter.ofPattern(datePattern())),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(greeting(state.profile?.displayName), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(24.dp))
        DayContent(vm, isToday = true, openLogId = openLog?.takeIf { it.date == today }?.logId, onLogOpened = onLogOpened, openSettings = openSettings)
    }
}

/** "Wednesday 14 October" in the phone's language. */
private fun datePattern(): String =
    android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEdMMMM")

@Composable
private fun greeting(name: String?): String {
    val hour = LocalTime.now().hour
    val part = when {
        hour < 5 -> 0
        hour < 12 -> 1
        hour < 18 -> 2
        else -> 3
    }
    val named = !name.isNullOrBlank()
    return when (part) {
        0 -> if (named) stringResource(R.string.today_greeting_night_name, name!!) else stringResource(R.string.today_greeting_night)
        1 -> if (named) stringResource(R.string.today_greeting_morning_name, name!!) else stringResource(R.string.today_greeting_morning)
        2 -> if (named) stringResource(R.string.today_greeting_afternoon_name, name!!) else stringResource(R.string.today_greeting_afternoon)
        else -> if (named) stringResource(R.string.today_greeting_evening_name, name!!) else stringResource(R.string.today_greeting_evening)
    }
}
