package io.github.stub1t.chronicals.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Entry
import io.github.stub1t.chronicals.data.EntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class RangeState(val loaded: Boolean = false, val types: Map<String, EntryType> = emptyMap(), val entries: Map<String, Entry> = emptyMap())

/** Entries of a range of days (a month, a week), reloaded when the range or the diary changes. */
class RangeViewModel(private val diary: Diary, initial: Pair<LocalDate, LocalDate>) : ViewModel() {
    val range = MutableStateFlow(initial)

    val state: StateFlow<RangeState> = combine(range, diary.changes) { (from, to), _ ->
        RangeState(true, diary.entryTypes().associateBy { it.id }, diary.entriesBetween(from.toString(), to.toString()).associateBy { it.date })
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, RangeState())

    companion object {
        fun month(m: YearMonth) = m.atDay(1) to m.atEndOfMonth()
    }
}

/** A day with something in it; a day that only has a place (set while travelling) counts as empty. */
fun isTracked(entry: Entry?): Boolean =
    entry != null && (entry.logs.isNotEmpty() || entry.triggers.isNotEmpty() || !entry.note.isNullOrBlank() || entry.wellbeing != null)

val WELLBEING_COLORS = listOf("#e57d90", "#ef9f66", "#e2b94f", "#86c98f", "#4fb487")
