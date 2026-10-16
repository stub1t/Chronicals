package io.github.stub1t.chronicals.data

import java.time.Duration
import kotlin.math.roundToInt

/**
 * The export report: a summary per entry type, the links found, the medications and the diary day by
 * day, as a spreadsheet (CSV) and as an HTML page to print or save as PDF. Language-dependent parts come
 * in through [Format]. Port of src/lib/report.
 */
object Report {
    class Format(
        /** Built-in text stored in English, translated. */
        val translate: (String) -> String,
        /** A clock time of a stored ISO time. */
        val time: (String) -> String,
        /** Name of a weather exposure ('pressure-drop') or pollen species ('birch'). */
        val exposureName: (kind: String, key: String) -> String,
    )

    class NamedLink(val link: Insights.Link, val name: String)
    class TypeSummary(val type: EntryType, val name: String, val days: Int, val share: Double, val avgIntensity: Double?, val typicalMinutes: Double?,
                      val reliefDays: Int, val links: List<NamedLink>)
    data class MedicationSummary(val medication: Medication, val changes: List<MedicationChange>)
    class Row(val type: String, val start: String, val end: String, val startTime: String, val endTime: String?, val durationMinutes: Long?,
              val intensity: Double?, val details: String, val taken: String)
    class Day(val date: String, val wellbeing: Int?, val triggers: List<String>, val note: String, val place: String?, val entries: List<Row>)
    class Data(val from: String, val to: String, val name: String?, val totalDays: Long, val trackedDays: Int, val types: List<TypeSummary>,
               val medications: List<MedicationSummary>, val days: List<Day>)

    /** Keys shown in their own column, or not at all. */
    private val HIDDEN_KEYS = setOf("intensity", "severity", "relief")

    private fun minutes(log: Log): Long? = log.endTime?.let { Duration.between(Times.parse(log.startTime), Times.parse(it)).toMinutes() }?.takeIf { it > 0 }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted(); val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
    }

    /** "Where it hurts: temples; Symptoms: aura, nausea; Blood sugar reading: 220 mg/dL" from a log's form values. */
    fun details(log: Log, type: EntryType?, translate: (String) -> String): String {
        val fields = type?.fields.orEmpty()
        val values = log.values
        fun label(key: String) = translate(fields.firstOrNull { it.key == key }?.label ?: key)
        fun show(value: Any?): String = when (value) {
            is List<*> -> value.map(::show).filter { it.isNotEmpty() }.joinToString(", ")
            is String -> translate(value)
            null -> ""
            is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
            else -> value.toString()
        }
        // A meal's kind is its name in the list; its foods and text are the details.
        val meal = type?.category == "meal"
        return values.entries
            .filter { (key, _) -> key !in HIDDEN_KEYS && !key.endsWith("_unit") && !(meal && key == "meal") }
            .map { (key, value) ->
                val field = fields.firstOrNull { it.key == key }
                // A 1 to 5 scale (a period's flow) reads like intensity: "3/5".
                val scale = field?.type == "slider" && field.min == 1 && field.max == 5 && value is Number
                val unit = if (scale) "/5" else (values["${key}_unit"] as? String)?.let { " $it" } ?: ""
                label(key) to show(value).let { if (it.isNotEmpty()) "$it$unit" else it }
            }
            .filter { it.second.isNotEmpty() }
            .joinToString("; ") { "${it.first}: ${it.second}" }
    }

    /** [entries] should start a day before [from], so "the day before" triggers work on the first day. [typeIds] empty: everything. */
    fun build(from: String, to: String, name: String?, entries: List<Entry>, weather: List<Weather>, types: List<EntryType>,
              medications: List<Medication>, changes: List<MedicationChange>, typeIds: Set<String>, format: Format): Data {
        val byId = types.associateBy { it.id }
        fun include(id: String) = typeIds.isEmpty() || id in typeIds
        val inRange = entries.filter { it.date in from..to }.sortedBy { it.date }
        val diary = Insights.index(entries, byId)
        val triggerNames = entries.flatMap { e -> e.triggers.map { it.triggerId to it.name } }.toMap()
        val tracked = inRange.count { diary[it.date]?.tracked == true }
        val summaries = types.filter { include(it.id) && it.category != "note" }.map { type ->
            val logs = inRange.flatMap { e -> e.logs.filter { it.entryTypeId == type.id }.map { e.date to it } }
            val days = logs.map { it.first }.toSet().size
            val analysed = type.category == "symptom" || type.category == "event"
            TypeSummary(type, format.translate(type.name), days, if (tracked > 0) days.toDouble() / tracked else 0.0,
                logs.mapNotNull { it.second.intensity }.takeIf { it.isNotEmpty() }?.average(),
                median(logs.mapNotNull { minutes(it.second)?.toDouble() }),
                if (analysed) Insights.overview(diary, type.id, from, to).reliefDays else 0,
                if (!analysed) emptyList() else Insights.links(diary, type.id, from, to, weather)
                    .filter { it.label == Insights.Label.Likely || it.label == Insights.Label.Fewer }
                    .map { l -> NamedLink(l, if (l.kind == "weather" || l.kind == "pollen") format.exposureName(l.kind, l.key)
                        else format.translate(if (l.kind == "food") l.key else triggerNames[l.key] ?: l.key)) })
        }.filter { it.days > 0 }
        val meds = medications.filter { it.startDate <= to && (it.endDate == null || it.endDate >= from) }.map { m ->
            MedicationSummary(m, changes.filter { it.medicationId == m.id && it.changeDate in from..to }.sortedBy { it.changeDate })
        }
        val days = inRange.map { e ->
            Day(e.date, e.wellbeing, e.triggers.map { format.translate(it.name) }, e.note?.trim().orEmpty(),
                if (e.locationLat != null) e.locationName ?: "" else null,
                e.logs.filter { include(it.entryTypeId) }.sortedBy { it.startTime }.map { log ->
                    val type = byId[log.entryTypeId]
                    Row(format.translate(((log.values["meal"] as? String)?.takeIf { type?.category == "meal" }) ?: type?.name ?: ""),
                        format.time(log.startTime), log.endTime?.let(format.time) ?: "", log.startTime, log.endTime, minutes(log), log.intensity,
                        details(log, type, format.translate), (log.values["relief"] as? List<*>)?.joinToString(", ") { format.translate(it.toString()) } ?: "")
                })
            // A report for some types lists only their days; a full report every day with anything in it.
        }.filter { d -> if (typeIds.isNotEmpty()) d.entries.isNotEmpty() else d.entries.isNotEmpty() || d.triggers.isNotEmpty() || d.note.isNotEmpty() || d.wellbeing != null }
        return Data(from, to, name, Insights.daysBetween(from, to) + 1, tracked, summaries, meds, days)
    }

    /** 153 → "2 h 33 min", 45 → "45 min". */
    fun formatDuration(minutes: Double?): String {
        if (minutes == null) return ""
        val r = minutes.roundToInt(); val h = r / 60; val m = r % 60
        return if (h > 0) (if (m > 0) "$h h $m min" else "$h h") else "$m min"
    }

    // ─── CSV ──────────────────────────────────────────────────────────────────

    /** A cell as text; one a spreadsheet would treat as a formula gets a leading apostrophe, so opening the file runs nothing. */
    private fun cell(value: Any?, separator: String): String {
        if (value == null) return ""
        var text = if (value is Double && value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        if (value is String && text.isNotEmpty() && text[0] in "=+-@\t\r") text = "'$text"
        return if (separator in text || text.any { it == '"' || it == '\r' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    /** Local 24-hour "HH:MM", whatever the clock setting: spreadsheets sort and read it. */
    private fun clock24(iso: String?) = iso?.let { Times.local(it).let { t -> "%02d:%02d".format(t.hour, t.minute) } } ?: ""

    /** One row per entry with the day's well-being, triggers, note and place repeated; a day without entries gets one row. UTF-8 with BOM for Excel. */
    fun csv(report: Data, separator: String, headings: List<String>, wellbeing: (Int) -> String): String {
        val rows = report.days.flatMap { d ->
            val day = listOf<Any?>(d.date, d.wellbeing?.let(wellbeing) ?: "", d.triggers.joinToString(", "), d.note, d.place ?: "")
            if (d.entries.isEmpty()) listOf(day + listOf("", "", "", null, null, null, "", ""))
            else d.entries.map { e -> day + listOf(e.type, clock24(e.startTime), clock24(e.endTime), e.durationMinutes, e.intensity, e.intensity?.let { 5 }, e.details, e.taken) }
        }
        return "﻿" + (listOf(headings) + rows).joinToString("\r\n") { row -> row.joinToString(separator) { cell(it, separator) } } + "\r\n"
    }

    // ─── HTML ─────────────────────────────────────────────────────────────────

    private fun esc(value: Any?): String = (value ?: "").toString()
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun table(head: List<String>, rows: List<List<Any?>>): String =
        "<table><thead><tr>" + head.joinToString("") { "<th>${esc(it)}</th>" } + "</tr></thead><tbody>" +
            rows.joinToString("") { r -> "<tr>" + r.joinToString("") { "<td>" + esc(it).replace("\n", "<br>") + "</td>" } + "</tr>" } + "</tbody></table>"

    class HtmlText(
        val t: (String, Array<String>) -> String,
        val date: (String) -> String,
        val day: (String) -> String,
        val wellbeing: (Int) -> String,
        val one: (Double) -> String,
        val percent: (Double) -> String,
        val lang: String,
        val rtl: Boolean,
        val created: String,
    )

    fun html(r: Data, x: HtmlText): String {
        fun t(key: String, vararg args: String) = x.t(key, arrayOf(*args))
        fun section(title: String, body: String) = "<section><h2>${esc(title)}</h2>$body</section>"
        fun empty(key: String) = "<p class=\"muted\">${esc(t(key))}</p>"
        val summary = if (r.types.isEmpty()) empty("report_empty") else table(
            listOf(t("report_col_type"), t("report_col_days"), t("report_col_share"), t("report_col_intensity"), t("report_col_duration"), t("report_col_relief")),
            r.types.map { s -> listOf(s.name, s.days, x.percent(s.share), s.avgIntensity?.let { "${x.one(it)}/5" } ?: "", formatDuration(s.typicalMinutes),
                if (s.type.category == "symptom") t("report_relief_days", s.reliefDays.toString(), s.days.toString()) else "") })
        val links = r.types.flatMap { s -> s.links.map { l ->
            listOf(s.name, l.name, t("insights_kind_${l.link.kind}"), t(if (l.link.lag == 1) "report_timing_next" else "report_timing_same"),
                x.percent(l.link.rates.withRate), x.percent(l.link.rates.withoutRate), l.link.rates.exposedDays,
                t(if (l.link.label == Insights.Label.Likely) "insights_label_likely" else "insights_label_fewer"))
        } }
        val linksBody = if (links.isEmpty()) empty("report_no_triggers") else table(
            listOf(t("report_col_type"), t("report_col_trigger"), t("report_col_kind"), t("report_col_timing"), t("report_col_with"), t("report_col_without"),
                t("report_col_seen"), t("report_col_assessment")), links) + "<p class=\"muted\">${esc(t("report_links_note"))}</p>"
        val meds = if (r.medications.isEmpty()) empty("report_no_medications") else table(
            listOf(t("report_col_medication"), t("report_col_dosage"), t("report_col_taken"), t("report_col_changes")),
            r.medications.map { (m, changes) -> listOf(m.name, listOfNotNull(m.dosage, m.unit).filter { it.isNotEmpty() }.joinToString(" "),
                t("report_period", x.date(m.startDate), m.endDate?.let(x.date) ?: t("report_ongoing")),
                changes.joinToString("\n") { c -> "${x.date(c.changeDate)}: " + when (c.changeType) {
                    "started" -> t("report_change_started", c.newDosage ?: "")
                    "stopped" -> t("report_change_stopped")
                    else -> t("report_change_dosage_change", c.oldDosage ?: "", c.newDosage ?: "")
                } }) })
        val head = listOf(t("report_col_time"), t("report_col_type"), t("report_col_intensity_single"), t("report_col_duration_single"), t("report_col_details"), t("report_col_relief_single"))
        val diary = if (r.days.isEmpty()) empty("report_empty") else "<table class=\"diary\"><thead><tr>" + head.joinToString("") { "<th>${esc(it)}</th>" } + "</tr></thead>" +
            r.days.joinToString("") { d ->
                val facts = listOfNotNull(d.wellbeing?.let { "${t("report_wellbeing")}: ${x.wellbeing(it)}" },
                    d.triggers.takeIf { it.isNotEmpty() }?.let { "${t("report_col_triggers")}: ${it.joinToString(", ")}" },
                    d.place?.let { "${t("report_place")}: ${it.ifEmpty { t("place_away") }}" })
                val dayRow = "<tr class=\"day\"><th colspan=\"6\">${esc(x.day(d.date))}" + (if (facts.isNotEmpty()) "<span>${facts.joinToString(" · ") { esc(it) }}</span>" else "") + "</th></tr>"
                val note = if (d.note.isNotEmpty()) "<tr class=\"note\"><td colspan=\"6\">${esc(d.note).replace("\n", "<br>")}</td></tr>" else ""
                val rows = d.entries.joinToString("") { e ->
                    "<tr>" + listOf(listOf(e.start, e.end).filter { it.isNotEmpty() }.joinToString(" – "), e.type, e.intensity?.let { "${x.one(it)}/5" } ?: "",
                        formatDuration(e.durationMinutes?.toDouble()), e.details, e.taken).joinToString("") { "<td>${esc(it)}</td>" } + "</tr>"
                }
                "<tbody>$dayRow$note$rows</tbody>"
            } + "</table>"
        val facts = (if (r.name != null) "<div><dt>${esc(t("report_name"))}</dt><dd>${esc(r.name)}</dd></div>" else "") +
            "<div><dt>${esc(t("report_period_label"))}</dt><dd>${esc(t("report_period", x.date(r.from), x.date(r.to)))}</dd></div>" +
            "<div><dt>${esc(t("report_tracked_days"))}</dt><dd>${esc(t("report_days_of", r.trackedDays.toString(), r.totalDays.toString()))}</dd></div>"
        return """<!doctype html>
<html lang="${esc(x.lang)}" dir="${if (x.rtl) "rtl" else "ltr"}">
<head>
<meta charset="utf-8">
<title>${esc(t("report_doc_title"))}</title>
<style>
	@page { size: A4; margin: 16mm 14mm 18mm; }
	* { box-sizing: border-box; }
	body { margin: 0; font: 10pt/1.4 system-ui, 'Roboto', 'Noto Sans', sans-serif; color: #1d1b19; }
	header { border-bottom: 2px solid #1d1b19; padding-bottom: 8pt; margin-bottom: 12pt; }
	h1 { font-size: 18pt; margin: 0 0 6pt; }
	dl { display: flex; flex-wrap: wrap; gap: 4pt 24pt; margin: 0; }
	dt { font-size: 8pt; color: #6b625a; text-transform: uppercase; letter-spacing: 0.04em; }
	dd { margin: 0; font-weight: 600; }
	h2 { font-size: 12pt; margin: 16pt 0 6pt; break-after: avoid; }
	table { width: 100%; border-collapse: collapse; font-size: 9pt; }
	th, td { text-align: start; vertical-align: top; padding: 3pt 5pt; border-bottom: 0.5pt solid #d8d2ca; }
	th { background: #efebe6; font-weight: 700; }
	tr { break-inside: avoid; }
	thead { display: table-header-group; }
	.diary tbody { break-inside: avoid; }
	.diary tr.day th { background: #f7f4f0; border-top: 1pt solid #b9b0a6; padding-top: 5pt; }
	.diary tr.day span { display: block; font-weight: 400; color: #4a433d; }
	.diary tr.note td { font-style: italic; color: #4a433d; }
	.diary td:first-child { white-space: nowrap; }
	.muted { color: #6b625a; font-size: 9pt; }
	footer { margin-top: 18pt; padding-top: 6pt; border-top: 0.5pt solid #d8d2ca; font-size: 8pt; color: #6b625a; }
</style>
</head>
<body>
<header>
<h1>${esc(t("report_doc_title"))}</h1>
<dl>$facts</dl>
</header>
${section(t("report_summary"), summary)}
${section(t("report_triggers"), linksBody)}
${section(t("report_medications"), meds)}
${section(t("report_diary"), diary)}
<footer>${esc(t("report_disclaimer"))} ${esc(t("report_created", x.created))}</footer>
</body>
</html>"""
    }
}
