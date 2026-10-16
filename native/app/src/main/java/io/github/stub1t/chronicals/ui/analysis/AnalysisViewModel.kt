package io.github.stub1t.chronicals.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Insights
import io.github.stub1t.chronicals.data.Times
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class TypeResult(
    val overview: Insights.Overview,
    /** Day grid up to half a year; month bars beyond. */
    val days: List<Pair<String, Double?>>?,
    val months: List<Insights.Bucket>,
    val links: List<Insights.Link>,
    val weekday: Insights.WeekdayPattern?,
    val time: Triple<String, Int, Int>?,
    val cycle: Insights.CycleResult?,
    val meds: List<Insights.MedicationEffect>,
)

class AllRow(val type: EntryType, val overview: Insights.Overview, val strip: List<Insights.Bucket>)

class AnalysisState(
    val loaded: Boolean = false,
    val period: String = "3m",
    val from: String = "",
    val to: String = "",
    val trackedDays: Int = 0,
    val length: Long = 0,
    val types: List<Pair<EntryType, Int>> = emptyList(),
    /** null: all types at once. */
    val selected: EntryType? = null,
    val result: TypeResult? = null,
    val all: List<AllRow>? = null,
    val wellbeing: List<Double?> = emptyList(),
    val triggerNames: Map<String, String> = emptyMap(),
)

/** Everything the Analysis tab shows, worked out off the main thread. Port of src/routes/insights. */
class AnalysisViewModel(private val diary: Diary) : ViewModel() {
    val period = MutableStateFlow("3m")
    /** An entry type id, 'all', or null for the default. */
    val type = MutableStateFlow<String?>(null)

    val state: StateFlow<AnalysisState> = combine(period, type, diary.changes) { p, t, _ -> compute(p, t) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AnalysisState())

    private fun compute(period: String, typeParam: String?): AnalysisState {
        val today = Times.today().toString()
        var from = when (period) { "6m" -> Insights.monthsBefore(today, 6); "12m" -> Insights.monthsBefore(today, 12); "all" -> "1900-01-01"; else -> Insights.monthsBefore(today, 3) }
        // The period before, for the trend, and one more day for "the day before" triggers.
        val loadFrom = if (period == "all") from else Insights.addDays(from, -(Insights.daysBetween(from, today) + 2))
        val types = diary.entryTypes()
        val byId = types.associateBy { it.id }
        val records = Insights.index(diary.entriesBetween(loadFrom, today), byId)
        if (period == "all") from = Insights.firstTrackedDay(records) ?: today
        val weather = diary.weatherBetween(Insights.addDays(from, -1), today)
        val tracked = Insights.trackedDays(records, from, today).size
        val length = Insights.daysBetween(from, today) + 1
        val analysable = Insights.analysableTypes(records, types, from, today)
        // "All" first when there is more than one type.
        val showAll = analysable.size > 1 && (typeParam == "all" || analysable.none { it.first.id == typeParam })
        val selected = if (showAll) null else analysable.firstOrNull { it.first.id == typeParam }?.first ?: analysable.firstOrNull()?.first
        val daily = length <= 190
        val base = AnalysisState(true, period, from, today, tracked, length, analysable, selected, triggerNames = diary.triggers().associate { it.id to it.name })

        if (selected != null) {
            val id = selected.id
            val result = TypeResult(
                overview = Insights.overview(records, id, from, today),
                days = if (daily) Insights.dailyLevels(records, id, from, today) else null,
                months = Insights.months(records, id, from, today),
                links = Insights.links(records, id, from, today, weather),
                weekday = Insights.weekdayPattern(Insights.byWeekday(records, id, from, today)),
                time = Insights.timePattern(Insights.byTimeOfDay(records, id, from, today)),
                cycle = Insights.byCyclePhase(records, id, "builtin-period", from, today),
                meds = Insights.medicationEffects(records, id, Insights.medicationEvents(diary.medications(), diary.medicationChanges()), from, today, today),
            )
            return AnalysisState(base.loaded, period, from, today, tracked, length, analysable, selected, result, null, emptyList(), base.triggerNames)
        }
        if (analysable.isEmpty()) return base
        fun strip(id: String) = if (daily) Insights.weeks(records, id, from, today) else Insights.months(records, id, from, today)
        val rows = analysable.map { (t, _) -> AllRow(t, Insights.overview(records, t.id, from, today), strip(t.id)) }
        return AnalysisState(base.loaded, period, from, today, tracked, length, analysable, null, null, rows,
            strip(analysable.first().first.id).map { it.avgWellbeing }, base.triggerNames)
    }
}
