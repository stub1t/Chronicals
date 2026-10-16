package io.github.stub1t.chronicals.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * The statistics behind the analysis. Port of src/lib/insights/engine.ts, fisher.ts and dates.ts.
 *
 * - A tracked day has any record: a log, a trigger, a note or a well-being rating.
 * - A symptom day is a tracked day with a log of the chosen entry type.
 * Untracked days are left out everywhere: no entry does not mean no symptom.
 */
object Insights {
    // ─── Dates on YYYY-MM-DD strings ──────────────────────────────────────────

    fun addDays(date: String, days: Long): String = LocalDate.parse(date).plusDays(days).toString()
    fun daysBetween(a: String, b: String): Long = ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b))
    /** 0 = Monday ... 6 = Sunday. */
    fun weekdayIndex(date: String): Int = LocalDate.parse(date).dayOfWeek.value - 1
    fun weekStart(date: String): String = addDays(date, -weekdayIndex(date).toLong())
    fun eachDay(from: String, to: String): List<String> {
        val days = mutableListOf<String>()
        var d = LocalDate.parse(from)
        val end = LocalDate.parse(to)
        while (!d.isAfter(end)) { days += d.toString(); d = d.plusDays(1) }
        return days
    }
    /** The first day of a period of [months] months ending on [today]: the same day that many months earlier, plus one. */
    fun monthsBefore(today: String, months: Long): String = LocalDate.parse(today).minusMonths(months).plusDays(1).toString()

    // ─── Fisher exact test ────────────────────────────────────────────────────

    private val logFactorials = mutableListOf(0.0)
    private fun logFactorial(n: Int): Double {
        synchronized(logFactorials) {
            for (i in logFactorials.size..n) logFactorials += logFactorials[i - 1] + ln(i.toDouble())
            return logFactorials[n]
        }
    }
    private fun logChoose(n: Int, k: Int) = logFactorial(n) - logFactorial(k) - logFactorial(n - k)

    /** One-sided Fisher exact test for a 2×2 table (exposed: a, b; not exposed: c, d). */
    fun fisherOneSided(a: Int, b: Int, c: Int, d: Int): Pair<Double, Double> {
        val exposed = a + b
        val symptom = a + c
        val total = a + b + c + d
        val lo = max(0, exposed - (total - symptom))
        val hi = min(symptom, exposed)
        val logAll = logChoose(total, exposed)
        var greater = 0.0
        var less = 0.0
        for (x in lo..hi) {
            val p = exp(logChoose(symptom, x) + logChoose(total - symptom, exposed - x) - logAll)
            if (x >= a) greater += p
            if (x <= a) less += p
        }
        return min(1.0, greater) to min(1.0, less)
    }

    // ─── The diary as days ────────────────────────────────────────────────────

    class DayRecord(
        val date: String,
        val tracked: Boolean,
        val logs: List<Log>,
        val types: Set<String>,
        val triggers: Set<String>,
        val foods: Set<String>,
        val wellbeing: Int?,
    )

    fun index(entries: Collection<Entry>, types: Map<String, EntryType>): Map<String, DayRecord> = entries.associate { entry ->
        val foods = entry.logs.filter { types[it.entryTypeId]?.category == "meal" }
            .flatMap { (it.values["foods"] as? List<*>)?.map { f -> f.toString() }.orEmpty() }.toSet()
        val triggers = entry.triggers.map { it.triggerId }.toSet()
        entry.date to DayRecord(
            entry.date,
            entry.logs.isNotEmpty() || triggers.isNotEmpty() || !entry.note.isNullOrBlank() || entry.wellbeing != null,
            entry.logs, entry.logs.map { it.entryTypeId }.toSet(), triggers, foods, entry.wellbeing,
        )
    }

    fun trackedDays(diary: Map<String, DayRecord>, from: String, to: String) = eachDay(from, to).filter { diary[it]?.tracked == true }
    fun firstTrackedDay(diary: Map<String, DayRecord>): String? = diary.values.filter { it.tracked }.minOfOrNull { it.date }

    private fun mean(values: List<Double>) = if (values.isEmpty()) null else values.sum() / values.size
    private fun logsOf(diary: Map<String, DayRecord>, days: List<String>, typeId: String) = days.flatMap { d -> diary[d]!!.logs.filter { it.entryTypeId == typeId } }
    private fun avgIntensity(logs: List<Log>) = mean(logs.mapNotNull { it.intensity })
    private fun tookSomething(log: Log) = (log.values["relief"] as? List<*>)?.isNotEmpty() == true

    // ─── How am I doing ───────────────────────────────────────────────────────

    open class PeriodCounts(val from: String, val to: String, val trackedDays: Int, val symptomDays: Int) {
        /** Symptom days per 30 tracked days; null without tracked days. */
        val per30: Double? = if (trackedDays > 0) symptomDays.toDouble() / trackedDays * 30 else null
    }

    class Overview(
        val current: PeriodCounts,
        val avgIntensity: Double?,
        /** Symptom days on which something was taken against it. */
        val reliefDays: Int,
        /** The period of the same length just before. */
        val previous: PeriodCounts,
        /** better, worse or same; null with too little data on either side. */
        val trend: String?,
    )

    const val MIN_TREND_DAYS = 14

    private fun counts(diary: Map<String, DayRecord>, typeId: String, from: String, to: String): PeriodCounts {
        val days = trackedDays(diary, from, to)
        return PeriodCounts(from, to, days.size, days.count { diary[it]!!.types.contains(typeId) })
    }

    fun overview(diary: Map<String, DayRecord>, typeId: String, from: String, to: String): Overview {
        val current = counts(diary, typeId, from, to)
        val length = daysBetween(from, to) + 1
        val previous = counts(diary, typeId, addDays(from, -length), addDays(from, -1))
        val days = trackedDays(diary, from, to)
        val reliefDays = days.count { d -> diary[d]!!.logs.any { it.entryTypeId == typeId && tookSomething(it) } }
        var trend: String? = null
        if (current.trackedDays >= MIN_TREND_DAYS && previous.trackedDays >= MIN_TREND_DAYS) {
            val now = current.per30!!
            val before = previous.per30!!
            val (greater, less) = fisherOneSided(current.symptomDays, current.trackedDays - current.symptomDays, previous.symptomDays, previous.trackedDays - previous.symptomDays)
            // A fifth fewer (or more), at least one day a month apart, and unlikely to be chance.
            trend = when {
                now <= before * 0.8 && before - now >= 1 && less < 0.2 -> "better"
                now >= before * 1.25 && now - before >= 1 && greater < 0.2 -> "worse"
                else -> "same"
            }
        }
        return Overview(current, avgIntensity(logsOf(diary, days, typeId)), reliefDays, previous, trend)
    }

    class Bucket(val start: String, val trackedDays: Int, val symptomDays: Int, val rate: Double?, val avgWellbeing: Double?)

    private fun bucket(diary: Map<String, DayRecord>, typeId: String, start: String, from: String, to: String): Bucket {
        val records = trackedDays(diary, from, to).map { diary[it]!! }
        val symptomDays = records.count { it.types.contains(typeId) }
        return Bucket(start, records.size, symptomDays, if (records.isNotEmpty()) symptomDays.toDouble() / records.size else null,
            mean(records.mapNotNull { it.wellbeing?.toDouble() }))
    }

    /** One bucket per week (Monday start), clipped to the period. */
    fun weeks(diary: Map<String, DayRecord>, typeId: String, from: String, to: String): List<Bucket> {
        val result = mutableListOf<Bucket>()
        var start = weekStart(from)
        while (start <= to) {
            val end = addDays(start, 6)
            result += bucket(diary, typeId, start, maxOf(start, from), minOf(end, to))
            start = addDays(start, 7)
        }
        return result
    }

    /** One bucket per calendar month, clipped to the period. */
    fun months(diary: Map<String, DayRecord>, typeId: String, from: String, to: String): List<Bucket> {
        val result = mutableListOf<Bucket>()
        var start = from.substring(0, 7) + "-01"
        while (start <= to) {
            val next = LocalDate.parse(start).plusMonths(1).toString()
            result += bucket(diary, typeId, start, maxOf(start, from), minOf(addDays(next, -1), to))
            start = next
        }
        return result
    }

    /** Per day: null when not tracked, 0 when tracked without the symptom, else the highest intensity (3 when not rated). */
    fun dailyLevels(diary: Map<String, DayRecord>, typeId: String, from: String, to: String): List<Pair<String, Double?>> = eachDay(from, to).map { date ->
        val day = diary[date]
        if (day?.tracked != true) return@map date to null
        val logs = day.logs.filter { it.entryTypeId == typeId }
        date to if (logs.isEmpty()) 0.0 else logs.maxOf { it.intensity ?: 3.0 }
    }

    // ─── What goes along with it ──────────────────────────────────────────────

    /** likely, watch (worth keeping an eye on), fewer, none or not-enough. */
    enum class Label { Likely, Fewer, Watch, None, NotEnough }

    class Rates(val exposedDays: Int, val unexposedDays: Int, val exposedSymptomDays: Int, val withRate: Double, val withoutRate: Double,
                /** withRate / withoutRate (infinite when the symptom never came without it). */ val ratio: Double, val greater: Double, val less: Double)

    const val MIN_EXPOSED_DAYS = 5

    private fun rates(a: Int, b: Int, c: Int, d: Int): Rates {
        val exposed = a + b
        val unexposed = c + d
        val withRate = if (exposed > 0) a.toDouble() / exposed else 0.0
        val withoutRate = if (unexposed > 0) c.toDouble() / unexposed else 0.0
        val ratio = if (withoutRate > 0) withRate / withoutRate else if (withRate > 0) Double.POSITIVE_INFINITY else 1.0
        val (greater, less) = fisherOneSided(a, b, c, d)
        return Rates(exposed, unexposed, a, withRate, withoutRate, ratio, greater, less)
    }

    /** Rates for one exposure: true, false, or null when unknown (left out). With lag 1 it is read from the day before. */
    private fun exposureRates(diary: Map<String, DayRecord>, typeId: String, from: String, to: String, exposure: (String) -> Boolean?, lag: Int): Rates {
        var a = 0; var b = 0; var c = 0; var d = 0
        for (date in trackedDays(diary, from, to)) {
            val exposed = exposure(if (lag == 1) addDays(date, -1) else date) ?: continue
            val symptom = diary[date]!!.types.contains(typeId)
            if (exposed) { if (symptom) a++ else b++ } else { if (symptom) c++ else d++ }
        }
        return rates(a, b, c, d)
    }

    val WEATHER_EXPOSURES: Map<String, (Weather) -> Boolean?> = linkedMapOf(
        "pressure-drop" to { w -> w.pressureDeltaHpa?.let { it <= -5 } },
        "hot-day" to { w -> w.temperatureC?.let { it >= 28 } },
        "high-uv" to { w -> w.uvIndex?.let { it >= 6 } },
    )

    val POLLEN = listOf("alder", "birch", "grass", "mugwort", "olive", "ragweed")
    private val POLLEN_BANDS = mapOf("alder" to (10.0 to 100.0), "birch" to (10.0 to 100.0), "olive" to (10.0 to 100.0),
        "grass" to (10.0 to 50.0), "mugwort" to (5.0 to 20.0), "ragweed" to (5.0 to 20.0))

    /** low, moderate or high, by the usual European bands per plant group (grains per m³, daily peak). */
    fun pollenLevel(species: String, value: Double?): String? {
        if (value == null) return null
        val (moderate, high) = POLLEN_BANDS.getValue(species)
        return if (value >= high) "high" else if (value >= moderate) "moderate" else "low"
    }

    class Link(val key: String, /** trigger, weather, pollen or food */ val kind: String, val lag: Int, val rates: Rates, val p: Double, val label: Label)

    /** Benjamini-Hochberg: which of the p-values hold at false discovery rate q. */
    fun benjaminiHochberg(ps: List<Double>, q: Double = 0.1): List<Boolean> {
        val order = ps.withIndex().sortedBy { it.value }
        var cutoff = -1
        order.forEachIndexed { rank, (_, p) -> if (p <= (rank + 1).toDouble() / order.size * q) cutoff = rank }
        val pass = MutableList(ps.size) { false }
        order.forEachIndexed { rank, (i, _) -> pass[i] = rank <= cutoff }
        return pass
    }

    /** Weather and pollen come in seasonal streaks, which makes chance links more likely; they need stronger evidence. */
    private val SEASONAL = setOf("weather", "pollen")

    /** Every trigger used, foods eaten on at least 5 days, the weather and pollen, each with the stronger of same day and day before. Clear links first. */
    fun links(diary: Map<String, DayRecord>, typeId: String, from: String, to: String, weather: List<Weather>): List<Link> {
        fun known(date: String) = diary[date]?.tracked == true
        val sources = mutableListOf<Triple<String, String, (String) -> Boolean?>>()
        val usedTriggers = linkedSetOf<String>()
        val foodDays = linkedMapOf<String, Int>()
        for (date in eachDay(addDays(from, -1), to)) {
            diary[date]?.triggers?.let(usedTriggers::addAll)
            diary[date]?.foods?.forEach { foodDays[it] = (foodDays[it] ?: 0) + 1 }
        }
        for (id in usedTriggers) sources += Triple(id, "trigger") { d: String -> if (known(d)) diary[d]!!.triggers.contains(id) else null }
        for ((food, n) in foodDays) if (n >= MIN_EXPOSED_DAYS) sources += Triple(food, "food") { d: String -> if (known(d)) diary[d]!!.foods.contains(food) else null }
        val byDate = weather.associateBy { it.date }
        for ((key, test) in WEATHER_EXPOSURES) sources += Triple(key, "weather") { d: String -> byDate[d]?.let(test) }
        for (species in POLLEN) {
            fun high(w: Weather?): Boolean? = pollenLevel(species, w?.pollen?.get(species))?.let { it == "high" }
            // Only species that reached "high" at all are worth testing.
            if (weather.none { high(it) == true }) continue
            sources += Triple(species, "pollen") { d: String -> high(byDate[d]) }
        }

        class Candidate(val key: String, val kind: String, val up: Boolean, val enough: Boolean, val lag: Int, val rates: Rates, val p: Double)
        val candidates = sources.map { (key, kind, exposure) ->
            val same = exposureRates(diary, typeId, from, to, exposure, 0)
            val before = exposureRates(diary, typeId, from, to, exposure, 1)
            val (lag, best) = if (min(same.greater, same.less) <= min(before.greater, before.less)) 0 to same else 1 to before
            val up = best.ratio >= 1
            Candidate(key, kind, up, best.exposedDays >= MIN_EXPOSED_DAYS && best.unexposedDays >= MIN_EXPOSED_DAYS, lag, best,
                min(1.0, 2 * (if (up) best.greater else best.less)))
        }
        val testable = candidates.filter { it.enough }
        val passed = benjaminiHochberg(testable.map { it.p }).withIndex().filter { it.value }.map { testable[it.index] }.toSet()

        val result = candidates.map { c ->
            val significant = c in passed
            val r = c.rates
            val label = when {
                !c.enough -> Label.NotEnough
                c.up && significant && c.p < (if (c.kind in SEASONAL) 0.01 else 0.05) && r.ratio >= 1.5 && r.exposedSymptomDays >= 3 -> Label.Likely
                !c.up && significant && c.p < 0.05 && r.ratio <= 0.67 -> Label.Fewer
                c.up && c.p < 0.2 && r.ratio >= 1.25 && r.exposedSymptomDays >= 2 -> Label.Watch
                else -> Label.None
            }
            Link(c.key, c.kind, c.lag, r, c.p, label)
        }
        return result.filter { it.rates.exposedDays > 0 }.sortedWith { x, y ->
            when {
                x.label != y.label -> x.label.ordinal - y.label.ordinal
                // Strongest first: highest ratio for links, lowest for "fewer".
                x.label == Label.Fewer -> x.rates.ratio.compareTo(y.rates.ratio)
                y.rates.ratio == x.rates.ratio -> x.p.compareTo(y.p)
                else -> y.rates.ratio.compareTo(x.rates.ratio)
            }
        }
    }

    // ─── When does it happen ──────────────────────────────────────────────────

    class Share(var trackedDays: Int = 0, var symptomDays: Int = 0, var rate: Double = 0.0)

    /** Monday first. */
    fun byWeekday(diary: Map<String, DayRecord>, typeId: String, from: String, to: String): List<Share> {
        val result = List(7) { Share() }
        for (date in trackedDays(diary, from, to)) {
            val slot = result[weekdayIndex(date)]
            slot.trackedDays++
            if (diary[date]!!.types.contains(typeId)) slot.symptomDays++
        }
        for (s in result) s.rate = if (s.trackedDays > 0) s.symptomDays.toDouble() / s.trackedDays else 0.0
        return result
    }

    class WeekdayPattern(val weekday: Int, val rate: Double, val otherRate: Double)

    /** The weekday that stands out, if one clearly does (one-sided Fisher, corrected for picking 1 of 7). */
    fun weekdayPattern(shares: List<Share>): WeekdayPattern? {
        var weekday = 0
        shares.forEachIndexed { i, s -> if (s.rate > shares[weekday].rate) weekday = i }
        val s = shares[weekday]
        val others = shares.filterIndexed { i, _ -> i != weekday }
        val tracked = others.sumOf { it.trackedDays }
        val symptom = others.sumOf { it.symptomDays }
        if (s.trackedDays < MIN_EXPOSED_DAYS || tracked < MIN_EXPOSED_DAYS) return null
        val (greater, _) = fisherOneSided(s.symptomDays, s.trackedDays - s.symptomDays, symptom, tracked - symptom)
        val otherRate = symptom.toDouble() / tracked
        return if (greater * 7 < 0.05 && s.rate >= otherRate * 1.5) WeekdayPattern(weekday, s.rate, otherRate) else null
    }

    val DAY_PARTS = listOf("night", "morning", "afternoon", "evening")

    /** How many logs started at night (0-6 h), in the morning, afternoon and evening, local time. */
    fun byTimeOfDay(diary: Map<String, DayRecord>, typeId: String, from: String, to: String): Map<String, Int> {
        val result = DAY_PARTS.associateWith { 0 }.toMutableMap()
        for (log in logsOf(diary, trackedDays(diary, from, to), typeId)) {
            val hour = Times.local(log.startTime).hour
            val part = if (hour < 6) "night" else if (hour < 12) "morning" else if (hour < 18) "afternoon" else "evening"
            result[part] = result.getValue(part) + 1
        }
        return result
    }

    /** The part of the day when most logs start, if it is at least half of 5 or more. */
    fun timePattern(parts: Map<String, Int>): Triple<String, Int, Int>? {
        val total = parts.values.sum()
        val (part, count) = parts.entries.sortedByDescending { it.value }.first().toPair()
        return if (total >= 5 && count.toDouble() / total >= 0.5) Triple(part, count, total) else null
    }

    class Phase(val phase: String, val trackedDays: Int, val symptomDays: Int, val rate: Double, val label: Label)
    class CycleResult(val cycles: Int, val phases: List<Phase>)

    /**
     * Symptom rate by cycle phase: the 3 days before a period, period days 1 to 3, later period days and
     * the rest. A start needs 10 period-free days before it. Needs two starts in the period.
     */
    fun byCyclePhase(diary: Map<String, DayRecord>, typeId: String, periodTypeId: String, from: String, to: String): CycleResult? {
        if (typeId == periodTypeId) return null
        fun hasPeriod(date: String) = diary[date]?.types?.contains(periodTypeId) == true
        val starts = eachDay(addDays(from, -45), addDays(to, 3)).filter { date -> hasPeriod(date) && (0 until 10).none { hasPeriod(addDays(date, -1L - it)) } }
        val cycles = starts.count { it in from..to }
        if (cycles < 2) return null

        fun phaseOf(date: String): String? {
            val next = starts.firstOrNull { it > date }
            if (next != null && daysBetween(date, next) <= 3) return "before"
            val last = starts.lastOrNull { it <= date }
            if (last != null) {
                if (daysBetween(last, date) <= 2) return "early"
                if (hasPeriod(date)) return "late"
            }
            // The last days of the period may be "before" a period that is not logged yet.
            if (next == null && daysBetween(date, to) < 3) return null
            return "rest"
        }

        val days = trackedDays(diary, from, to).mapNotNull { date -> phaseOf(date)?.let { it to diary[date]!!.types.contains(typeId) } }
        val phases = listOf("before", "early", "late", "rest").map { phase ->
            val inside = days.filter { it.first == phase }
            val outside = days.filter { it.first != phase }
            val a = inside.count { it.second }
            val c = outside.count { it.second }
            val r = rates(a, inside.size - a, c, outside.size - c)
            val enough = inside.size >= MIN_EXPOSED_DAYS && outside.size >= MIN_EXPOSED_DAYS
            // Three phases are tested, so the bar is p < 0.05 / 3.
            val label = when {
                phase == "rest" -> Label.None
                !enough -> Label.NotEnough
                r.greater * 3 < 0.05 && r.ratio >= 1.5 -> Label.Likely
                r.greater < 0.2 && r.ratio >= 1.25 -> Label.Watch
                else -> Label.None
            }
            Phase(phase, inside.size, a, if (inside.isNotEmpty()) a.toDouble() / inside.size else 0.0, label)
        }
        return CycleResult(cycles, phases)
    }

    // ─── Do my medications help ───────────────────────────────────────────────

    class MedicationEvent(val medicationId: String, val name: String, val date: String, /** started or dosage_change */ val kind: String,
                          val oldDosage: String?, val newDosage: String?, val stopDate: String?, val forTypes: List<String>)

    /** Starts and dose changes of every medication, newest first. */
    fun medicationEvents(medications: List<Medication>, changes: List<MedicationChange>): List<MedicationEvent> {
        val events = mutableListOf<MedicationEvent>()
        for (med in medications) {
            val dose = med.dosage?.takeIf { it.isNotEmpty() }?.let { if (!med.unit.isNullOrEmpty()) "$it ${med.unit}" else it }
            val own = changes.filter { it.medicationId == med.id }.sortedBy { it.changeDate }
            val stops = own.filter { it.changeType == "stopped" }.map { it.changeDate }
            fun stopAfter(date: String) = stops.firstOrNull { it >= date } ?: med.endDate?.takeIf { it >= date }
            // The first dose change tells which dose the medication was started with.
            val firstChange = own.firstOrNull { it.changeType == "dosage_change" }
            events += MedicationEvent(med.id, med.name, med.startDate, "started", null, if (firstChange != null) firstChange.oldDosage else dose, stopAfter(med.startDate), med.forTypes)
            for (change in own) {
                if (change.changeType == "stopped") continue
                if (change.changeType == "started" && change.changeDate == med.startDate) continue
                events += MedicationEvent(med.id, med.name, change.changeDate, change.changeType, change.oldDosage, change.newDosage, stopAfter(change.changeDate), med.forTypes)
            }
        }
        return events.sortedByDescending { it.date }
    }

    class Side(from: String, to: String, trackedDays: Int, symptomDays: Int, val avgIntensity: Double?) : PeriodCounts(from, to, trackedDays, symptomDays)
    class MedicationEffect(val event: MedicationEvent, val before: Side, val after: Side, /** fewer, more, similar or null */ val result: String?,
                           val alsoChanged: List<String>, val linked: Boolean)

    const val MEDICATION_WINDOW = 30L
    const val MIN_MEDICATION_DAYS = 10

    /** The 30 days before each event against the days from it on (up to 30, today, or the stop). */
    fun medicationEffects(diary: Map<String, DayRecord>, typeId: String, events: List<MedicationEvent>, from: String, to: String, today: String): List<MedicationEffect> =
        events.filter { it.date in from..to }.filter { it.forTypes.isEmpty() || typeId in it.forTypes }.map { event ->
            val beforeFrom = addDays(event.date, -MEDICATION_WINDOW)
            val beforeTo = addDays(event.date, -1)
            val afterTo = (listOf(addDays(event.date, MEDICATION_WINDOW - 1), today) + listOfNotNull(event.stopDate?.let { addDays(it, -1) })).min()
            fun side(start: String, end: String): Side {
                val c = counts(diary, typeId, start, if (end < start) start else end)
                return Side(c.from, c.to, c.trackedDays, c.symptomDays, avgIntensity(logsOf(diary, trackedDays(diary, start, end), typeId)))
            }
            val before = side(beforeFrom, beforeTo)
            val after = side(event.date, afterTo)
            var result: String? = null
            if (before.trackedDays >= MIN_MEDICATION_DAYS && after.trackedDays >= MIN_MEDICATION_DAYS) {
                val r = rates(after.symptomDays, after.trackedDays - after.symptomDays, before.symptomDays, before.trackedDays - before.symptomDays)
                result = if (r.less < 0.1 && r.ratio <= 0.67) "fewer" else if (r.greater < 0.1 && r.ratio >= 1.5) "more" else "similar"
            }
            val also = events.filter { it.medicationId != event.medicationId && it.date >= beforeFrom && it.date <= afterTo }.map { it.name }.distinct()
            MedicationEffect(event, before, after, result, also, typeId in event.forTypes)
        }

    /** Entry types worth analysing (symptoms and events with logs in the period), most logged first. */
    fun analysableTypes(diary: Map<String, DayRecord>, types: List<EntryType>, from: String, to: String): List<Pair<EntryType, Int>> {
        val days = trackedDays(diary, from, to)
        return types.filter { it.category == "symptom" || it.category == "event" }
            .map { t -> t to days.count { diary[it]!!.types.contains(t.id) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }
    }
}
