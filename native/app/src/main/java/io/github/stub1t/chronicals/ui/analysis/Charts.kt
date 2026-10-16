package io.github.stub1t.chronicals.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Insights
import io.github.stub1t.chronicals.ui.number
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil

val FreeGreen = Color(0xFF86C98F)

private fun monthShort(date: String) = LocalDate.parse(date).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/**
 * One square per day, a column per week (Monday on top), months named above: empty when not recorded,
 * soft green without the symptom, the type's colour by intensity otherwise. Tap a day to see it.
 * Port of src/lib/insights/charts/Heatmap.svelte.
 */
@Composable
fun Heatmap(days: List<Pair<String, Double?>>, color: Color, typeName: String, label: String) {
    val lead = if (days.isEmpty()) 0 else Insights.weekdayIndex(days[0].first)
    val cells: List<Pair<String, Double?>?> = List(lead) { null } + days
    val columns = ceil(cells.size / 7.0).toInt()
    val cell = 12.dp; val gap = 3.dp
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    LaunchedEffect(days) { scroll.scrollTo(scroll.maxValue) }
    var picked by remember(days) { mutableStateOf<Pair<String, Double?>?>(null) }
    val empty = MaterialTheme.colorScheme.outlineVariant
    val ring = MaterialTheme.colorScheme.onSurface
    // A month name above the column where the month's first day falls; one that would crowd the next is dropped.
    val labels = cells.mapIndexedNotNull { i, c -> if (c != null && (c.first.endsWith("-01") || i == lead)) i / 7 to monthShort(c.first) else null }
        .let { l -> l.filterIndexed { i, x -> i + 1 >= l.size || l[i + 1].first - x.first >= 3 } }

    Column {
        Column(Modifier.horizontalScroll(scroll)) {
            Box(Modifier.width((cell + gap) * columns).height(18.dp)) {
                for ((column, text) in labels) Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = (cell + gap) * column))
            }
            Canvas(
                Modifier.width((cell + gap) * columns).height((cell + gap) * 7)
                    .semantics { contentDescription = label }
                    .pointerInput(cells) {
                        detectTapGestures { pos ->
                            val step = with(density) { (cell + gap).toPx() }
                            val i = (pos.x / step).toInt() * 7 + (pos.y / step).toInt()
                            val c = cells.getOrNull(i)
                            picked = if (c == null || picked?.first == c.first) null else c
                        }
                    },
            ) {
                val s = cell.toPx(); val step = (cell + gap).toPx(); val r = CornerRadius(3.dp.toPx())
                cells.forEachIndexed { i, c ->
                    if (c == null) return@forEachIndexed
                    val o = Offset((i / 7) * step, (i % 7) * step)
                    val level = c.second
                    when {
                        level == null -> drawRoundRect(empty, o, Size(s, s), r, style = Stroke(1.dp.toPx()))
                        level == 0.0 -> drawRoundRect(FreeGreen.copy(alpha = 0.45f), o, Size(s, s), r)
                        else -> drawRoundRect(color.copy(alpha = (0.2f + level.toFloat() * 0.16f).coerceAtMost(1f)), o, Size(s, s), r)
                    }
                    if (picked?.first == c.first) drawRoundRect(ring, o, Size(s, s), r, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Legend(Modifier, empty, outlined = true); Text(stringResource(R.string.insights_heatmap_untracked), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
            Legend(Modifier, FreeGreen.copy(alpha = 0.45f)); Text(stringResource(R.string.insights_heatmap_free), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(6.dp))
            Legend(Modifier, color.copy(alpha = 0.36f)); Legend(Modifier, color); Text(stringResource(R.string.insights_heatmap_strong), style = MaterialTheme.typography.labelSmall)
        }
        picked?.let { (date, level) ->
            val day = LocalDate.parse(date)
            val text = when {
                level == null -> stringResource(R.string.insights_day_none)
                level == 0.0 -> stringResource(R.string.insights_day_free, typeName)
                else -> stringResource(R.string.insights_day_level, typeName, number(level))
            }
            Text("${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.dayOfMonth} ${monthShort(date)}: $text",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Legend(modifier: Modifier, color: Color, outlined: Boolean = false) {
    Canvas(modifier.size(11.dp)) {
        val r = CornerRadius(3.dp.toPx())
        if (outlined) drawRoundRect(color, cornerRadius = r, style = Stroke(1.dp.toPx())) else drawRoundRect(color, cornerRadius = r)
    }
}

/** Share of recorded days with the symptom, one bar per month; months with few recorded days are faint. */
@Composable
fun MonthBars(months: List<Insights.Bucket>, color: Color, label: String) {
    val every = maxOf(1, ceil(months.size / 6.0).toInt())
    Column(Modifier.semantics { contentDescription = label }) {
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            for (m in months) Box(Modifier.weight(1f).height(120.dp), contentAlignment = Alignment.BottomCenter) {
                m.rate?.let { rate ->
                    Box(Modifier.fillMaxWidth().fillMaxHeight(maxOf(rate.toFloat(), 0.02f)).clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = if (m.trackedDays < 5) 0.35f else 0.85f)))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            months.forEachIndexed { i, m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (i % every == 0) monthShort(m.start) else "", style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                    Text(if (i == 0 || m.start.substring(5, 7) == "01") m.start.substring(0, 4) else "", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

class RateRow(val label: String, val value: Double, val detail: String, val highlight: Boolean, val faint: Boolean)

/** Labelled horizontal bars with a share and its detail. Port of src/lib/insights/charts/RateBars.svelte. */
@Composable
fun RateBars(rows: List<RateRow>, color: Color) {
    val top = maxOf(0.01, rows.maxOfOrNull { it.value } ?: 0.0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in rows) Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (row.highlight) FontWeight.Bold else null)
                Text(percent(row.value), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth((row.value / top).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = if (row.faint) 0.3f else if (row.highlight) 1f else 0.55f)))
            }
            Text(row.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun percent(v: Double): String = java.text.NumberFormat.getPercentInstance().apply { maximumFractionDigits = 0 }.format(v)

/** A small line of values between [min] and [max]; gaps where a value is missing. */
@Composable
fun Sparkline(values: List<Double?>, min: Double, max: Double, color: Color, label: String) {
    Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = label }) {
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        val path = Path()
        var open = false
        values.forEachIndexed { i, v ->
            if (v == null) { open = false; return@forEachIndexed }
            val p = Offset(i * step, size.height - ((v - min) / (max - min)).toFloat() * size.height)
            if (open) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
            open = true
            drawCircle(color, 2.5.dp.toPx(), p)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
    }
}
