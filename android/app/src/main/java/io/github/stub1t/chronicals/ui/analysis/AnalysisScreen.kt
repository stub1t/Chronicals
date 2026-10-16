package io.github.stub1t.chronicals.ui.analysis

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Insights
import io.github.stub1t.chronicals.data.Insights.Label
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.day.Section
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.number
import io.github.stub1t.chronicals.ui.typeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Danger = Color(0xFFD9485F)
private val Positive = Color(0xFF2FAE78)

private fun fmt(date: String, skeleton: String) =
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)))

/** */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(diary: Diary, openReport: () -> Unit = {}) {
    val vm: AnalysisViewModel = viewModel { AnalysisViewModel(diary) }
    val state by vm.state.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.nav_insights), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            val options = listOf("3m" to R.string.insights_period_3m, "6m" to R.string.insights_period_6m, "12m" to R.string.insights_period_12m, "all" to R.string.insights_period_all)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(period == value, { vm.period.value = value }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(stringResource(label)) }
                }
            }
            if (state.loaded) Text(
                stringResource(R.string.insights_range, fmt(state.from, "dMMMyyyy"), fmt(state.to, "dMMMyyyy"), state.trackedDays.toString(), state.length.toString()),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!state.loaded) return@Column

        if (state.types.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.insights_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.insights_empty_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        if (state.types.size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.selected == null, { vm.type.value = "all" }, { Text(stringResource(R.string.insights_all)) })
            for ((type, _) in state.types) FilterChip(state.selected?.id == type.id, { vm.type.value = type.id }, { Text(localize(type.name)) },
                leadingIcon = { EntryIcon(type, 18.dp) })
        }
        if (state.trackedDays < 28) Text(pluralStringResource(R.plurals.insights_few_days, state.trackedDays, state.trackedDays.toString()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val selected = state.selected
        val result = state.result
        if (state.all != null) AllTypes(state) { vm.type.value = it }
        else if (selected != null && result != null) {
            val color = typeColor(selected)
            val name = localize(selected.name)
            Section(stringResource(R.string.insights_doing)) { SummaryCard(name, color, result) }
            Section(stringResource(R.string.insights_links)) { LinksCard(name, color, result.links, state.triggerNames) }
            Section(stringResource(R.string.insights_when)) { WhenCard(name, color, result) }
            if (result.meds.isNotEmpty()) Section(stringResource(R.string.insights_meds)) { MedsCard(name, color, result.meds) }
        }

        Surface(onClick = openReport, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.report_title), fontWeight = FontWeight.Bold)
                    Muted(stringResource(R.string.insights_report_text), small = true)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
        }
    }
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Muted(text: String, small: Boolean = false) =
    Text(text, style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun TrendIcon(trend: String, size: Int = 18) = Icon(
    when (trend) { "better" -> Icons.AutoMirrored.Outlined.TrendingDown; "worse" -> Icons.AutoMirrored.Outlined.TrendingUp; else -> Icons.Outlined.Remove },
    null, Modifier.size(size.dp), tint = trendColor(trend),
)

@Composable
private fun trendColor(trend: String) = when (trend) { "better" -> Positive; "worse" -> Danger; else -> MaterialTheme.colorScheme.onSurfaceVariant }

@Composable
private fun trendText(trend: String) = stringResource(when (trend) { "better" -> R.string.insights_trend_better; "worse" -> R.string.insights_trend_worse; else -> R.string.insights_trend_same })

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(typeName: String, color: Color, result: TypeResult) {
    val o = result.overview
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(o.current.symptomDays.toString(), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Column {
                Text(pluralStringResource(R.plurals.insights_days_with, o.current.symptomDays, typeName), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Muted(pluralStringResource(R.plurals.insights_of_recorded, o.current.trackedDays, o.current.trackedDays.toString()))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            o.avgIntensity?.let { Fact(stringResource(R.string.insights_intensity_avg, number(it))) }
            o.current.per30?.let { Fact(stringResource(R.string.insights_per30_short, number(it))) }
            if (o.reliefDays > 0) Fact(stringResource(R.string.insights_relief, o.reliefDays.toString(), o.current.symptomDays.toString()))
        }
        o.trend?.let { trend ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(trendColor(trend).copy(alpha = 0.12f)).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrendIcon(trend); Spacer(Modifier.width(6.dp))
                    Text(trendText(trend), fontWeight = FontWeight.Bold, color = trendColor(trend))
                }
                Text(stringResource(R.string.insights_trend_detail, fmt(o.previous.from, "dMMM"), fmt(o.previous.to, "dMMM"), number(o.previous.per30 ?: 0.0), number(o.current.per30 ?: 0.0)),
                    style = MaterialTheme.typography.bodySmall, color = trendColor(trend))
            }
        }
        val label = stringResource(R.string.insights_timeline_label, o.current.symptomDays.toString(), typeName, o.current.trackedDays.toString())
        if (result.days != null) {
            Text(stringResource(R.string.insights_every_day), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Heatmap(result.days, color, typeName, label)
        } else {
            Text(stringResource(R.string.insights_by_month), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MonthBars(result.months, color, label)
        }
    }
}

@Composable
private fun Fact(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun linkName(link: Insights.Link, names: Map<String, String>): String = when (link.kind) {
    "weather" -> stringResource(when (link.key) { "pressure-drop" -> R.string.insights_weather_pressure_drop; "hot-day" -> R.string.insights_weather_hot_day; else -> R.string.insights_weather_high_uv })
    "pollen" -> stringResource(R.string.insights_pollen_high, stringResource(pollenName(link.key)))
    "food" -> localize(link.key)
    else -> localize(names[link.key] ?: link.key)
}

private fun pollenName(key: String) = when (key) {
    "alder" -> R.string.pollen_alder; "birch" -> R.string.pollen_birch; "grass" -> R.string.pollen_grass
    "mugwort" -> R.string.pollen_mugwort; "olive" -> R.string.pollen_olive; else -> R.string.pollen_ragweed
}

/** Inside a sentence a name starts in lower case ("after poor sleep"), except in German and for abbreviations. */
private fun inSentence(name: String): String =
    if (Locale.getDefault().language == "de" || (name.length > 1 && name[0].isUpperCase() && name[1].isUpperCase())) name
    else name.replaceFirstChar { it.lowercase(Locale.getDefault()) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksCard(typeName: String, color: Color, links: List<Insights.Link>, names: Map<String, String>) {
    val shown = links.filter { it.label == Label.Likely || it.label == Label.Fewer || it.label == Label.Watch }
    val rest = links.filter { it.label == Label.None || it.label == Label.NotEnough }
    Card {
        if (links.isEmpty()) Muted(stringResource(R.string.insights_no_triggers))
        else {
            if (shown.isEmpty()) Muted(stringResource(R.string.insights_nothing_found, typeName))
            for (link in shown) Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                val name = linkName(link, names)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    val (pillText, pillColor) = when (link.label) {
                        Label.Likely -> stringResource(R.string.insights_label_likely) to Danger
                        Label.Fewer -> stringResource(R.string.insights_label_fewer) to Positive
                        else -> stringResource(R.string.insights_label_watch) to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(pillText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = pillColor,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(pillColor.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 3.dp))
                }
                val kind = stringResource(when (link.kind) { "food" -> R.string.insights_kind_food; "weather" -> R.string.insights_kind_weather; "pollen" -> R.string.insights_kind_pollen; else -> R.string.insights_kind_trigger })
                Muted("$kind · " + stringResource(if (link.lag == 1) R.string.insights_lag_next else R.string.insights_lag_same), small = true)
                var sentence = stringResource(if (link.lag == 1) R.string.insights_link_next else R.string.insights_link_same,
                    typeName, percent(link.rates.withRate), inSentence(name), percent(link.rates.withoutRate))
                if (link.label != Label.Fewer && link.rates.ratio.isFinite()) sentence += " " + stringResource(R.string.insights_ratio, number(link.rates.ratio))
                Text(sentence, style = MaterialTheme.typography.bodyMedium)
                CompareBar(stringResource(if (link.lag == 1) R.string.insights_with_next else R.string.insights_with), link.rates.withRate, color, strong = true)
                CompareBar(stringResource(R.string.insights_without), link.rates.withoutRate, color, strong = false)
                Muted(pluralStringResource(R.plurals.insights_link_days, link.rates.exposedDays, link.rates.exposedDays.toString()), small = true)
            }
            if (rest.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Column(Modifier.animateContentSize()) {
                    TextButton(onClick = { open = !open }) { Text((if (open) "▾ " else "▸ ") + stringResource(R.string.insights_rest, rest.size.toString())) }
                    if (open) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (link in rest) Text(linkName(link, names), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (link.label == Label.NotEnough) 0.6f else 1f),
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Muted(stringResource(R.string.insights_rest_hint), small = true)
                    }
                }
            }
        }
        Muted(stringResource(R.string.insights_disclaimer), small = true)
    }
}

@Composable
private fun CompareBar(label: String, value: Double, color: Color, strong: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(84.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(value.toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = if (strong) 1f else 0.4f)))
        }
        Text(percent(value), Modifier.width(48.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WhenCard(typeName: String, color: Color, result: TypeResult) {
    Card {
        val weekday = result.weekday
        val time = result.time
        if (weekday == null && time == null) Muted(stringResource(R.string.insights_when_nothing))
        weekday?.let {
            val day = java.time.DayOfWeek.of(it.weekday + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())
            Row { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.insights_weekday_sentence, typeName, day, percent(it.rate), percent(it.otherRate))) }
        }
        time?.let { (part, count, total) ->
            val partText = stringResource(when (part) { "night" -> R.string.insights_at_night; "morning" -> R.string.insights_at_morning; "afternoon" -> R.string.insights_at_afternoon; else -> R.string.insights_at_evening })
            Row { Icon(Icons.Outlined.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.insights_time_sentence, partText, count.toString(), total.toString())) }
        }
        result.cycle?.let { cycle ->
            Text(stringResource(R.string.insights_cycle), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            RateBars(cycle.phases.map { p ->
                RateRow(stringResource(when (p.phase) { "before" -> R.string.insights_phase_before; "early" -> R.string.insights_phase_early; "late" -> R.string.insights_phase_late; else -> R.string.insights_phase_rest }),
                    p.rate, stringResource(R.string.insights_days_of, p.symptomDays.toString(), p.trackedDays.toString()),
                    p.label == Label.Likely || p.label == Label.Watch, p.trackedDays < 5)
            }, color)
            Muted(pluralStringResource(R.plurals.insights_cycle_note, cycle.cycles, cycle.cycles.toString()), small = true)
        }
    }
}

@Composable
private fun MedsCard(typeName: String, color: Color, effects: List<Insights.MedicationEffect>) {
    Card {
        Muted(stringResource(R.string.insights_meds_intro))
        for (e in effects) Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            val title = if (e.event.kind == "started") stringResource(R.string.insights_med_started, e.event.name, e.event.newDosage ?: "").trim()
                else stringResource(R.string.insights_med_dose, e.event.name, e.event.oldDosage ?: "?", e.event.newDosage ?: "?")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Medication, null, tint = color); Spacer(Modifier.width(8.dp))
                Column { Text(title, fontWeight = FontWeight.Bold); Muted(fmt(e.event.date, "dMMMyyyy"), small = true) }
            }
            Muted(stringResource(R.string.insights_meds_windows, fmt(e.before.from, "dMMM"), fmt(e.before.to, "dMMM"), fmt(e.after.from, "dMMM"), fmt(e.after.to, "dMMM")), small = true)
            if (e.result == null) Muted(stringResource(R.string.insights_med_unknown))
            else {
                val scale = maxOf(e.before.per30 ?: 0.0, e.after.per30 ?: 0.0, 1.0)
                for ((label, side) in listOf(R.string.insights_before to e.before, R.string.insights_after to e.after)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Box(Modifier.fillMaxWidth(((side.per30 ?: 0.0) / scale).toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp))
                                .background(color.copy(alpha = if (label == R.string.insights_after) 1f else 0.45f)))
                        }
                        Column(Modifier.padding(start = 8.dp).width(110.dp)) {
                            Text(stringResource(R.string.insights_per30_short, side.per30?.let(::number) ?: "–"), style = MaterialTheme.typography.labelMedium)
                            side.avgIntensity?.let { Muted(stringResource(R.string.insights_intensity_short, number(it)), small = true) }
                        }
                    }
                }
                val (text, c) = when (e.result) {
                    "fewer" -> stringResource(R.string.insights_med_fewer, typeName) to Positive
                    "more" -> stringResource(R.string.insights_med_more, typeName) to Danger
                    else -> stringResource(R.string.insights_med_similar) to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(text, color = c, fontWeight = FontWeight.Bold)
            }
            if (e.alsoChanged.isNotEmpty()) Muted(stringResource(R.string.insights_meds_also, e.alsoChanged.joinToString(", ")), small = true)
            if (!e.linked) Muted(stringResource(R.string.insights_meds_unlinked), small = true)
        }
        Muted(stringResource(R.string.insights_meds_note), small = true)
    }
}

/** Every analysed type at once: how often, how strong, the trend and a small strip. Tap for details. */
@Composable
private fun AllTypes(state: AnalysisState, onPick: (String) -> Unit) {
    val rows = state.all ?: return
    if (state.wellbeing.any { it != null }) Card {
        Text(stringResource(R.string.insights_wellbeing), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Sparkline(state.wellbeing, 1.0, 5.0, Positive, stringResource(R.string.insights_wellbeing))
        Row { Muted(stringResource(R.string.wellbeing_1), small = true); Spacer(Modifier.weight(1f)); Muted(stringResource(R.string.wellbeing_5), small = true) }
    }
    val top = maxOf(0.01, rows.flatMap { r -> r.strip.map { it.rate ?: 0.0 } }.maxOrNull() ?: 0.0)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column {
            for (row in rows) {
                val color = typeColor(row.type)
                Row(Modifier.fillMaxWidth().clickable { onPick(row.type.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    EntryIcon(row.type, 40.dp, badge = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(localize(row.type.name), fontWeight = FontWeight.Bold)
                        var meta = pluralStringResource(R.plurals.insights_all_days, row.overview.current.symptomDays, row.overview.current.symptomDays.toString(), state.trackedDays.toString())
                        row.overview.avgIntensity?.let { meta += " · ${number(it)}/5" }
                        Muted(meta, small = true)
                        Row(Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                            for (b in row.strip) Box(Modifier.weight(1f).fillMaxHeight(((b.rate ?: 0.0) / top).toFloat().coerceAtLeast(0.08f))
                                .clip(RoundedCornerShape(2.dp)).background(if (b.rate == null) MaterialTheme.colorScheme.surfaceContainerHigh else color.copy(alpha = 0.8f)))
                        }
                    }
                    row.overview.trend?.let { Spacer(Modifier.width(8.dp)); TrendIcon(it) }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Muted(stringResource(R.string.insights_all_hint), small = true)
}

