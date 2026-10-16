package io.github.stub1t.chronicals

import io.github.stub1t.chronicals.data.DayTrigger
import io.github.stub1t.chronicals.data.Entry
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Insights
import io.github.stub1t.chronicals.data.Insights.Label
import io.github.stub1t.chronicals.data.Insights.addDays
import io.github.stub1t.chronicals.data.Log
import io.github.stub1t.chronicals.data.Medication
import io.github.stub1t.chronicals.data.MedicationChange
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.data.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The cases the web app's insights tests had, so the statistics did not change in the port. */
class InsightsTest {
    private val HEADACHE = "builtin-headache"
    private val PERIOD = "builtin-period"
    private val MEAL = EntryType("builtin-meal", "Meal", null, null, emptyList(), "meal", 6, true, false, emptyMap())
    private val TYPES = mapOf(MEAL.id to MEAL)

    class Spec(var types: MutableList<String> = mutableListOf(), var triggers: MutableList<String> = mutableListOf(),
               val intensity: Double? = null, val wellbeing: Int? = null, val hour: Int = 9)

    /** Every listed date is a tracked day. */
    private fun diaryOf(days: Map<String, Spec>): List<Entry> = days.map { (date, s) ->
        Entry(date, date, if (s.types.isEmpty() && s.triggers.isEmpty() && s.wellbeing == null) "tracked" else null, s.wellbeing, null, null, null, null,
            logs = s.types.mapIndexed { i, type ->
                val start = LocalDate.parse(date).atTime(s.hour, i).atZone(Times.zone()).toInstant()
                Log("$date-$type", date, type, Times.iso(start), Times.iso(start.plusSeconds(7200)), emptyMap(), s.intensity)
            },
            triggers = s.triggers.map { DayTrigger("$date-$it", it, it, "custom", null, false) })
    }
    private fun index(days: Map<String, Spec>) = Insights.index(diaryOf(days), TYPES)

    @Test fun dates() {
        assertEquals("2026-03-30", addDays("2026-03-28", 2))
        assertEquals("2026-02-28", addDays("2026-03-01", -1))
        assertEquals(364L, Insights.daysBetween("2026-01-01", "2026-12-31"))
        assertEquals(0, Insights.weekdayIndex("2026-10-12"))
        assertEquals("2026-10-12", Insights.weekStart("2026-10-18"))
        assertEquals(listOf("2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02"), Insights.eachDay("2026-10-30", "2026-11-02"))
        assertEquals("2026-07-15", Insights.monthsBefore("2026-10-14", 3))
        assertEquals("2026-03-01", Insights.monthsBefore("2026-05-31", 3))
        assertEquals("2026-03-01", Insights.monthsBefore("2026-08-31", 6))
        assertEquals("2026-10-01", Insights.monthsBefore("2026-12-31", 3))
        assertEquals("2025-03-16", Insights.monthsBefore("2026-03-15", 12))
    }

    @Test fun fisher() {
        assertEquals(0.242857, Insights.fisherOneSided(3, 1, 1, 3).first, 1e-5)
        assertEquals(0.0244755, Insights.fisherOneSided(8, 2, 1, 5).first, 1e-6)
        assertEquals(0.00137973, Insights.fisherOneSided(1, 9, 11, 3).second, 1e-7)
        assertEquals(0.000103943, Insights.fisherOneSided(12, 8, 10, 60).first, 1e-8)
        val (g, l) = Insights.fisherOneSided(0, 5, 0, 5)
        assertEquals(1.0, g, 1e-9); assertEquals(1.0, l, 1e-9)
    }

    @Test fun benjaminiHochberg() {
        assertEquals(listOf(true, true, false, false), Insights.benjaminiHochberg(listOf(0.001, 0.04, 0.3, 0.5), 0.1))
        assertEquals(listOf(true, false), Insights.benjaminiHochberg(listOf(0.04, 0.2), 0.1))
        assertEquals(listOf(true, true, true), Insights.benjaminiHochberg(listOf(0.06, 0.07, 0.08), 0.1))
        assertEquals(emptyList<Boolean>(), Insights.benjaminiHochberg(emptyList()))
    }

    private fun triggerDays(): LinkedHashMap<String, Spec> {
        val days = linkedMapOf<String, Spec>()
        val start = "2026-01-01"
        for (i in 0 until 40) days[addDays(start, i.toLong())] = Spec(triggers = if (i % 4 == 2) mutableListOf("caffeine") else mutableListOf())
        for (i in 0 until 40 step 4) days[addDays(start, i.toLong())]!!.triggers += "poor-sleep"
        for (i in 1 until 40 step 4) days[addDays(start, i.toLong())]!!.types = mutableListOf(HEADACHE)
        days[addDays(start, 30)]!!.types = mutableListOf(HEADACHE)
        return days
    }

    @Test fun triggerLinks() {
        val days = triggerDays()
        val diary = index(days)
        val result = Insights.links(diary, HEADACHE, "2026-01-01", addDays("2026-01-01", 39), emptyList())
        val sleep = result.first { it.key == "poor-sleep" }
        assertEquals(1, sleep.lag); assertEquals(Label.Likely, sleep.label); assertEquals(10, sleep.rates.exposedDays)
        assertEquals(1.0, sleep.rates.withRate, 0.0)
        assertEquals("poor-sleep", result[0].key)
        assertTrue(result.first { it.key == "caffeine" }.label in setOf(Label.None, Label.Fewer))
        // The day before the first tracked day is unknown, so the day-before count has 39 days.
        assertEquals(39, sleep.rates.exposedDays + sleep.rates.unexposedDays)
        val weather = days.keys.mapIndexed { i, date -> Weather(date, 15.0, null, if (i % 4 == 1) -7.0 else 1.0, null, emptyMap(), 2.0) }
        val drop = Insights.links(diary, HEADACHE, "2026-01-01", addDays("2026-01-01", 39), weather).first { it.key == "pressure-drop" }
        assertEquals("weather", drop.kind); assertEquals(0, drop.lag); assertEquals(Label.Likely, drop.label)
    }

    @Test fun overviewAndSeries() {
        val days = linkedMapOf<String, Spec>()
        for (i in 0 until 60) days[addDays("2026-01-01", i.toLong())] =
            Spec(types = if ((if (i < 30) i % 3 else i % 6) == 0) mutableListOf(HEADACHE) else mutableListOf(), intensity = 4.0, wellbeing = 3)
        val diary = index(days)
        val o = Insights.overview(diary, HEADACHE, "2026-01-31", "2026-03-01")
        assertEquals(30, o.current.trackedDays); assertEquals(5, o.current.symptomDays); assertEquals(5.0, o.current.per30!!, 1e-9)
        assertEquals("better", o.trend); assertEquals(4.0, o.avgIntensity!!, 0.0); assertEquals(0, o.reliefDays)
        assertEquals(30, o.previous.trackedDays); assertEquals(10, o.previous.symptomDays)
        assertNull(Insights.overview(diary, HEADACHE, "2026-03-01", "2026-03-05").trend)
        val w = Insights.weeks(diary, HEADACHE, "2026-01-31", "2026-03-01")
        assertEquals(Insights.weekStart("2026-01-31"), w[0].start)
        assertEquals(30, w.sumOf { it.trackedDays })
        assertTrue(w.all { it.avgWellbeing == 3.0 })
        assertEquals(listOf(0.0, null, null), Insights.dailyLevels(diary, HEADACHE, "2026-03-01", "2026-03-03").map { it.second })
        assertEquals(30, Insights.byWeekday(diary, HEADACHE, "2026-01-31", "2026-03-01").sumOf { it.trackedDays })
        val parts = Insights.byTimeOfDay(index(mapOf("2026-01-01" to Spec(mutableListOf(HEADACHE), hour = 2), "2026-01-02" to Spec(mutableListOf(HEADACHE), hour = 19))),
            HEADACHE, "2026-01-01", "2026-01-02")
        assertEquals(mapOf("night" to 1, "morning" to 0, "afternoon" to 0, "evening" to 1), parts)
    }

    @Test fun cyclePhases() {
        val days = linkedMapOf<String, Spec>()
        for (i in 0 until 112) days[addDays("2026-01-01", i.toLong())] = Spec()
        for (cycle in 0 until 4) {
            val s = cycle * 28 + 10
            for (k in 0 until 5) days[addDays("2026-01-01", (s + k).toLong())]!!.types += PERIOD
            days[addDays("2026-01-01", (s - 1).toLong())]!!.types += HEADACHE
            days[addDays("2026-01-01", (s - 2).toLong())]!!.types += HEADACHE
        }
        val diary = index(days)
        val result = Insights.byCyclePhase(diary, HEADACHE, PERIOD, "2026-01-01", addDays("2026-01-01", 111))!!
        assertEquals(4, result.cycles)
        val before = result.phases.first { it.phase == "before" }
        assertEquals(8.0 / 12, before.rate, 1e-9); assertEquals(Label.Likely, before.label)
        assertEquals(0, result.phases.first { it.phase == "early" }.symptomDays)
        assertNull(Insights.byCyclePhase(diary, PERIOD, PERIOD, "2026-01-01", "2026-04-01"))
        assertNull(Insights.byCyclePhase(index(mapOf("2026-01-01" to Spec(mutableListOf(PERIOD)))), HEADACHE, PERIOD, "2026-01-01", "2026-01-02"))
    }

    private fun med(id: String, name: String, start: String, dosage: String? = null, unit: String? = null, end: String? = null, forTypes: List<String> = emptyList()) =
        Medication(id, name, dosage, unit, start, end, null, end == null, emptyList(), forTypes)
    private fun change(med: String, type: String, date: String, old: String? = null, new: String? = null) = MedicationChange("c-$date", med, type, old, new, date, null, null)

    @Test fun medications() {
        val mag = med("mag", "Magnesium", "2026-01-01", "600", "mg")
        val changes = listOf(change("mag", "dosage_change", "2026-03-02", "400 mg", "600 mg"))
        val events = Insights.medicationEvents(listOf(mag), changes)
        assertEquals(listOf(Triple("2026-03-02", "dosage_change", "600 mg"), Triple("2026-01-01", "started", "400 mg")), events.map { Triple(it.date, it.kind, it.newDosage) })
        val days = linkedMapOf<String, Spec>()
        for (i in -30 until 30) days[addDays("2026-03-02", i.toLong())] = Spec(if (if (i < 0) i % 2 == 0 else i % 6 == 0) mutableListOf(HEADACHE) else mutableListOf())
        val effect = Insights.medicationEffects(index(days), HEADACHE, events, "2026-02-01", "2026-04-30", "2026-04-30")[0]
        assertEquals("dosage_change", effect.event.kind)
        assertEquals(30, effect.before.trackedDays); assertEquals(15, effect.before.symptomDays)
        assertEquals(30, effect.after.trackedDays); assertEquals(5, effect.after.symptomDays)
        assertEquals("fewer", effect.result)
        val few = index(mapOf("2026-03-01" to Spec(), "2026-03-03" to Spec()))
        assertNull(Insights.medicationEffects(few, HEADACHE, events, "2026-02-01", "2026-04-30", "2026-04-30")[0].result)
    }

    private fun random(seed: Long): () -> Double {
        var s = seed
        return { s = (s * 1664525 + 1013904223) % 4294967296; s.toDouble() / 4294967296 }
    }

    @Test fun rarelyCallsUnrelatedTriggersLikely() {
        var likely = 0; var tested = 0
        for (seed in 1L..40L) {
            val rand = random(seed)
            val days = linkedMapOf<String, Spec>()
            for (i in 0 until 90) {
                val types = if (rand() < 0.25) mutableListOf(HEADACHE) else mutableListOf()
                days[addDays("2026-01-01", i.toLong())] = Spec(types, (0 until 10).map { "t$it" }.filter { rand() < 0.2 }.toMutableList())
            }
            val result = Insights.links(index(days), HEADACHE, "2026-01-01", addDays("2026-01-01", 89), emptyList())
            likely += result.count { it.label == Label.Likely }; tested += result.size
        }
        assertTrue(likely.toDouble() / tested <= 0.015)
    }

    @Test fun doublesThePForPickingTheBetterDay() {
        val days = linkedMapOf<String, Spec>()
        for (i in 0 until 30) days[addDays("2026-01-01", i.toLong())] = Spec(if (i % 3 == 0) mutableListOf(HEADACHE) else mutableListOf(), if (i % 3 == 0) mutableListOf("x") else mutableListOf())
        val link = Insights.links(index(days), HEADACHE, "2026-01-01", "2026-01-30", emptyList())[0]
        assertEquals(0, link.lag)
        assertEquals(2 * Insights.fisherOneSided(10, 0, 0, 20).first, link.p, 1e-10)
    }

    @Test fun noTrendOnOneDayOfDifference() {
        val days = linkedMapOf<String, Spec>()
        for (i in 0 until 14) {
            days[addDays("2026-01-01", i.toLong())] = Spec(if (i < 3) mutableListOf(HEADACHE) else mutableListOf())
            days[addDays("2026-01-15", i.toLong())] = Spec(if (i < 2) mutableListOf(HEADACHE) else mutableListOf())
        }
        assertEquals("same", Insights.overview(index(days), HEADACHE, "2026-01-15", "2026-01-28").trend)
        assertNull(Insights.overview(index(days), HEADACHE, "2026-01-22", "2026-01-28").trend)
    }

    @Test fun noSecondPeriodAcrossAGap() {
        val days = linkedMapOf<String, Spec>()
        for (i in 0 until 60) days[addDays("2026-01-01", i.toLong())] = Spec()
        for (d in listOf("2026-01-10", "2026-01-11", "2026-01-13", "2026-01-14")) days[d] = Spec(mutableListOf(PERIOD))
        days.remove("2026-01-12")
        assertNull(Insights.byCyclePhase(index(days), HEADACHE, PERIOD, "2026-01-01", "2026-03-01"))
    }

    @Test fun endsTheComparisonWhenStopped() {
        val m = med("m", "Magnesium", "2026-03-01", "400", "mg", end = "2026-03-10")
        val event = Insights.medicationEvents(listOf(m), listOf(change("m", "stopped", "2026-03-10")))[0]
        assertEquals("2026-03-10", event.stopDate)
        val days = linkedMapOf<String, Spec>()
        for (i in -30 until 30) days[addDays("2026-03-01", i.toLong())] = Spec()
        val effect = Insights.medicationEffects(index(days), HEADACHE, listOf(event), "2026-02-01", "2026-04-30", "2026-04-30")[0]
        assertEquals("2026-03-01", effect.after.from); assertEquals("2026-03-09", effect.after.to); assertEquals(9, effect.after.trackedDays)
        assertNull(effect.result)
    }

    @Test fun onlyMedicationsForTheTypeAndOtherChanges() {
        val a = med("a", "Cetirizine", "2026-03-01", forTypes = listOf("builtin-allergy"))
        val b = med("b", "Magnesium", "2026-03-05")
        val effects = Insights.medicationEffects(emptyMap(), HEADACHE, Insights.medicationEvents(listOf(a, b), emptyList()), "2026-02-01", "2026-04-30", "2026-04-30")
        assertEquals(listOf("Magnesium"), effects.map { it.event.name })
        assertEquals(listOf("Cetirizine"), effects[0].alsoChanged)
    }

    @Test fun noStartingDoseWhenUnknown() {
        val m = med("m", "X", "2026-03-01", "20", "mg")
        val events = Insights.medicationEvents(listOf(m), listOf(change("m", "dosage_change", "2026-03-10", null, "20 mg")))
        assertNull(events.first { it.kind == "started" }.newDosage)
    }

    @Test fun fewerStrongestFirstAndFoods() {
        val days = linkedMapOf<String, Spec>()
        for (i in 0 until 60) {
            val run = i % 2 == 0
            days[addDays("2026-01-01", i.toLong())] = Spec(if (!run && i % 3 != 0) mutableListOf(HEADACHE) else mutableListOf(),
                listOfNotNull(if (run) "run" else null, if (i % 5 == 0) "walk" else null).toMutableList())
        }
        val entries = diaryOf(days).map { e ->
            if (e.logs.any { it.entryTypeId == HEADACHE } && e.date < "2026-01-20")
                e.copy(logs = e.logs + Log("${e.date}-meal", e.id, MEAL.id, "${e.date}T10:00:00.000Z", null, mapOf("foods" to listOf("pasta")), null))
            else e
        }
        val result = Insights.links(Insights.index(entries, TYPES), HEADACHE, "2026-01-01", "2026-03-01", emptyList())
        assertEquals("run", result.first { it.label == Label.Fewer }.key)
        assertTrue(result.any { it.kind == "food" && it.key == "pasta" })
    }

    @Test fun pollenIsAnExposure() {
        val days = linkedMapOf<String, Spec>()
        val weather = mutableListOf<Weather>()
        for (i in 0 until 40) {
            val date = addDays("2026-04-01", i.toLong())
            val high = i % 4 == 0
            days[date] = Spec(if (high) mutableListOf(HEADACHE) else mutableListOf())
            weather += Weather(date, null, null, null, null, mapOf("birch" to if (high) 200.0 else 5.0), null)
        }
        val birch = Insights.links(index(days), HEADACHE, "2026-04-01", "2026-05-10", weather).first { it.kind == "pollen" }
        assertEquals("birch", birch.key); assertEquals(Label.Likely, birch.label); assertEquals(0, birch.lag)
    }
}
