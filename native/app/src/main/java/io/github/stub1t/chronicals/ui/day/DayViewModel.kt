package io.github.stub1t.chronicals.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Entry
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Medication
import io.github.stub1t.chronicals.data.Profile
import io.github.stub1t.chronicals.data.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayState(
    val loaded: Boolean = false,
    val types: List<EntryType> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
    val entry: Entry? = null,
    /** Entry type and trigger ids, most used in the last 90 days first. */
    val typeRanking: List<String> = emptyList(),
    val triggerRanking: List<String> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val profile: Profile? = null,
    val weather: io.github.stub1t.chronicals.data.Weather? = null,
) {
    fun hasTrigger(id: String) = entry?.triggers?.any { it.triggerId == id } == true
}

/** One diary day for Today and the calendar's day page. Reloads off the main thread after every change to the diary. */
class DayViewModel(val diary: Diary, val date: LocalDate) : ViewModel() {
    val state: StateFlow<DayState> = diary.changes
        .map { load() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayState())

    private fun load(): DayState {
        val recent = diary.entriesBetween(date.minusDays(90).toString(), date.toString())
        return DayState(
            loaded = true,
            types = diary.entryTypes(),
            triggers = diary.triggers(),
            entry = recent.firstOrNull { it.date == date.toString() },
            typeRanking = rank(recent.flatMap { e -> e.logs.map { it.entryTypeId } }),
            triggerRanking = rank(recent.flatMap { e -> e.triggers.map { it.triggerId } }),
            medications = diary.medications(),
            profile = diary.profile(),
            weather = diary.weatherBetween(date.toString(), date.toString()).firstOrNull(),
        )
    }

    /** Fetches the day's weather if it is on and missing or stale; the state follows when it is stored. */
    fun loadWeather() {
        viewModelScope.launch(Dispatchers.IO) { io.github.stub1t.chronicals.platform.WeatherSource.ensure(diary, listOf(date.toString())) }
    }

    /** Runs a write on a background thread; the state follows through [Diary.changes]. */
    fun write(block: Diary.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { diary.block() }
    }

    companion object {
        fun rank(ids: List<String>): List<String> = ids.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
}
