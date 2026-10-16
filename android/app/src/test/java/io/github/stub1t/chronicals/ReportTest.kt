package io.github.stub1t.chronicals

import io.github.stub1t.chronicals.data.DayTrigger
import io.github.stub1t.chronicals.data.Entry
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Field
import io.github.stub1t.chronicals.data.Log
import io.github.stub1t.chronicals.data.Medication
import io.github.stub1t.chronicals.data.MedicationChange
import io.github.stub1t.chronicals.data.Report
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/** The cases the web app's report tests had (run in Pacific/Auckland time there too). */
class ReportTest {
    @Before fun zone() = TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))

    private fun type(id: String, name: String, category: String, vararg fields: Field) = EntryType(id, name, null, null, fields.toList(), category, 0, true, false, emptyMap())
    private val headache = type("builtin-headache", "Headache", "symptom", Field("intensity", "Intensity", "slider", false), Field("location", "Location", "select", false),
        Field("symptoms", "Symptoms", "checklist", false))
    private val allergy = type("builtin-allergy", "Allergy", "symptom")

    private class L(val start: String, val end: String? = null, val intensity: Int? = null, val values: Map<String, Any?> = emptyMap())
    private fun entry(date: String, logs: List<L>, note: String? = null, wellbeing: Int? = null, triggers: List<DayTrigger> = emptyList()) =
        Entry(date, date, note, wellbeing, null, null, null, null, logs.mapIndexed { i, l ->
            Log("$date-$i", date, headache.id, "${date}T${l.start}:00Z", l.end?.let { "${date}T$it:00Z" },
                (if (l.intensity != null) mapOf("intensity" to l.intensity) else emptyMap<String, Any?>()) + l.values, l.intensity?.toDouble())
        }, triggers)

    private val german = mapOf("Headache" to "Kopfschmerz", "Location" to "Ort", "temples" to "Schläfen", "Symptoms" to "Symptome", "nausea" to "Übelkeit",
        "Stress" to "Stress", "Painkiller" to "Schmerzmittel")
    private val format = Report.Format({ german[it] ?: it }, { it.substring(11, 16) }, { _, key -> key })

    private val entries = listOf(
        entry("2026-08-31", listOf(L("08:00", intensity = 5))),
        entry("2026-09-03", listOf(L("08:00", "10:30", 4, mapOf("location" to "temples", "symptoms" to listOf("nausea"), "relief" to listOf("Painkiller")))),
            note = "Long day", wellbeing = 2, triggers = listOf(DayTrigger("t", "trig-stress", "Stress", "activity", null, true))),
        entry("2026-09-10", listOf(L("14:00", "14:30", 2), L("20:00", intensity = 3))),
        entry("2026-09-12", emptyList(), note = "=HYPERLINK(\"http://evil\")"),
    )
    private val meds = listOf(
        Medication("m1", "Magnesium", "400", "mg", "2026-08-01", null, null, true, emptyList(), emptyList()),
        Medication("m2", "Old", null, null, "2026-01-01", "2026-02-01", null, false, emptyList(), emptyList()),
    )
    private val changes = listOf(
        MedicationChange("c1", "m1", "dosage_change", "400 mg", "600 mg", "2026-09-15", null, null),
        MedicationChange("c2", "m1", "started", null, "400 mg", "2026-08-01", null, null),
    )
    private fun report(typeIds: Set<String> = emptySet(), list: List<Entry> = entries, types: List<EntryType> = listOf(headache, allergy)) =
        Report.build("2026-09-01", "2026-09-30", "Alex", list, emptyList(), types, meds, changes, typeIds, format)

    @Test fun daysOfThePeriod() {
        val r = report()
        assertEquals(listOf("2026-09-03", "2026-09-10", "2026-09-12"), r.days.map { it.date })
        assertEquals(3, r.trackedDays); assertEquals(30L, r.totalDays)
        val d = r.days[0]
        assertEquals(2, d.wellbeing); assertEquals(listOf("Stress"), d.triggers); assertEquals("Long day", d.note); assertEquals(null, d.place)
    }

    @Test fun entriesWithDetails() {
        val e = report().days[0].entries.single()
        assertEquals("Kopfschmerz", e.type); assertEquals("08:00", e.start); assertEquals("10:30", e.end); assertEquals(150L, e.durationMinutes)
        assertEquals(4.0, e.intensity!!, 0.0); assertEquals("Ort: Schläfen; Symptome: Übelkeit", e.details); assertEquals("Schmerzmittel", e.taken)
        assertEquals(listOf("14:00" to "14:30", "20:00" to ""), report().days[1].entries.map { it.start to it.end })
    }

    @Test fun summaryPerType() {
        val s = report().types.single()
        assertEquals("Kopfschmerz", s.name); assertEquals(2, s.days); assertEquals(2.0 / 3, s.share, 1e-9)
        assertEquals(3.0, s.avgIntensity!!, 0.0); assertEquals(90.0, s.typicalMinutes!!, 0.0); assertEquals(1, s.reliefDays)
        assertTrue(s.links.isEmpty())
    }

    @Test fun medicationsInThePeriod() {
        val r = report()
        assertEquals(listOf("Magnesium"), r.medications.map { it.medication.name })
        assertEquals(listOf("c1"), r.medications[0].changes.map { it.id })
    }

    @Test fun onlySomeTypes() {
        val only = report(setOf("builtin-allergy"))
        assertTrue(only.types.isEmpty()); assertTrue(only.days.isEmpty())
    }

    @Test fun unitsAndMeals() {
        val sugar = type("builtin-hypo", "Blood sugar", "event", Field("blood_glucose", "Reading", "number", false))
        val meal = type("builtin-meal", "Meal", "meal", Field("foods", "Foods", "checklist", false))
        val day = Entry("d", "2026-09-20", null, null, null, null, null, null, listOf(
            Log("a", "d", sugar.id, "2026-09-20T09:00:00Z", null, mapOf("blood_glucose" to 220, "blood_glucose_unit" to "mg/dL"), null),
            Log("b", "d", meal.id, "2026-09-20T12:00:00Z", null, mapOf("meal" to "lunch", "foods" to listOf("pasta")), null),
        ), emptyList())
        val r = report(list = listOf(day), types = listOf(sugar, meal))
        assertEquals(listOf("Blood sugar" to "Reading: 220 mg/dL", "lunch" to "Foods: pasta"), r.days[0].entries.map { it.type to it.details })
    }

    @Test fun scaleAsOutOfFive() {
        val period = type("builtin-period", "Period", "symptom", Field("flow", "Flow", "slider", false, 1, 5))
        val day = Entry("d", "2026-09-20", null, null, null, null, null, null,
            listOf(Log("p", "d", period.id, "2026-09-20T09:00:00Z", null, mapOf("flow" to 4), null)), emptyList())
        assertEquals("Flow: 4/5", report(list = listOf(day), types = listOf(period)).days[0].entries[0].details)
    }

    private val headings = listOf("Date", "Well-being", "Triggers", "Note", "Place", "Type", "Start", "End", "Minutes", "Intensity", "Scale", "Details", "Taken")
    private val wellbeing = { n: Int -> listOf("", "very bad", "bad", "so-so", "good", "very good")[n] }

    @Test fun csvRepeatsTheDay() {
        val lines = Report.csv(report(), ";", headings, wellbeing).split("\r\n")
        assertEquals("﻿Date;Well-being;Triggers;Note;Place;Type;Start;End;Minutes;Intensity;Scale;Details;Taken", lines[0])
        assertEquals("2026-09-03;bad;Stress;Long day;;Kopfschmerz;20:00;22:30;150;4;5;\"Ort: Schläfen; Symptome: Übelkeit\";Schmerzmittel", lines[1])
        assertEquals("2026-09-10;;;;;Kopfschmerz;02:00;02:30;30;2;5;;", lines[2])
        assertEquals("2026-09-10;;;;;Kopfschmerz;08:00;;;3;5;;", lines[3])
    }

    @Test fun csvDayWithoutEntriesAndFormulas() {
        val csv = Report.csv(report(), ",", headings, wellbeing)
        assertTrue(csv.split("\r\n")[4].matches(Regex("""^2026-09-12,,,"'=HYPERLINK\(""http://evil""\)",,,,,,,,,$""")))
        assertFalse(Regex("(^|,)=HYPERLINK", RegexOption.MULTILINE).containsMatchIn(csv))
    }

    @Test fun durations() {
        assertEquals("2 h 33 min", Report.formatDuration(153.0)); assertEquals("2 h", Report.formatDuration(120.0))
        assertEquals("45 min", Report.formatDuration(45.0)); assertEquals("", Report.formatDuration(null))
    }

    @Test fun htmlEscapesTheDiary() {
        val day = entry("2026-09-05", listOf(L("08:00", intensity = 4)), note = "</td><script>alert(1)</script><img src=x>")
        val html = Report.html(report(list = entries + day), Report.HtmlText({ k, a -> (listOf(k) + a).joinToString(" ") }, { it }, { "day $it" }, { "mood $it" },
            { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }, { "${(it * 100).toInt()}%" }, "de", false, "today"))
        assertTrue(html.startsWith("<!doctype html>")); assertTrue(html.contains("<html lang=\"de\" dir=\"ltr\">"))
        for (key in listOf("report_summary", "report_triggers", "report_medications", "report_diary", "report_disclaimer")) assertTrue(key, html.contains(key))
        assertTrue(html.contains("day 2026-09-03")); assertTrue(html.contains("report_wellbeing: mood 2")); assertTrue(html.contains("<td>4/5</td>"))
        assertFalse(html.contains("<script")); assertFalse(html.contains("<img")); assertTrue(html.contains("&lt;/td&gt;&lt;script&gt;"))
    }
}
