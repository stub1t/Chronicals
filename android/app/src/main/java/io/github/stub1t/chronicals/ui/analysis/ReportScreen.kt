package io.github.stub1t.chronicals.ui.analysis

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Insights
import io.github.stub1t.chronicals.data.Report
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.LocalSnackbar
import io.github.stub1t.chronicals.ui.clock
import io.github.stub1t.chronicals.ui.day.Label
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.me.DateDialog
import io.github.stub1t.chronicals.ui.me.FormCard
import io.github.stub1t.chronicals.ui.me.Hint
import io.github.stub1t.chronicals.ui.me.SubPage
import io.github.stub1t.chronicals.ui.number
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun fmt(date: String, skeleton: String) =
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)))

/** A string resource by name, for the report template. */
private fun Context.t(key: String, args: Array<String>): String {
    val id = resources.getIdentifier(key, "string", packageName)
    return if (id == 0) key else getString(id, *args)
}

private fun build(context: Context, diary: Diary, from: String, to: String, typeIds: Set<String>): Report.Data {
    fetchWeather(diary, from, to)
    return Report.build(from, to, diary.profile().displayName, diary.entriesBetween(Insights.addDays(from, -1), to),
        diary.weatherBetween(Insights.addDays(from, -1), to), diary.entryTypes(), diary.medications(), diary.medicationChanges(), typeIds,
        Report.Format(::localize, { clock(context, it) }, { kind, key ->
            if (kind == "weather") context.t("insights_weather_" + key.replace('-', '_'), emptyArray())
            else context.t("insights_pollen_high", arrayOf(context.t("pollen_$key", emptyArray())))
        }))
}

/** Weather for the period (and the day before), fetched where missing, only with weather on. */
private fun fetchWeather(diary: Diary, from: String, to: String) =
    io.github.stub1t.chronicals.platform.WeatherSource.ensure(diary, Insights.eachDay(Insights.addDays(from, -1), to))

private fun wellbeingWord(context: Context, v: Int) = context.t("wellbeing_$v", emptyArray())

/** A period and the types to include, then the PDF through the system print dialog and the spreadsheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(diary: Diary, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val today = Times.today()
    var range by remember { mutableStateOf("3") }
    var from by remember { mutableStateOf(today.minusMonths(3).plusDays(1).toString()) }
    var to by remember { mutableStateOf(today.toString()) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var picking by remember { mutableStateOf<String?>(null) }
    val types = remember { diary.entryTypes() }
    val csvName = "chronicals-report-$from-$to.csv"

    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val report = build(context, diary, from, to, picked)
                    // Where the decimal mark is a comma, spreadsheets expect ';' between cells.
                    val separator = if (number(1.5).contains(',')) ";" else ","
                    val headings = listOf("report_col_date", "report_csv_wellbeing", "report_csv_triggers", "report_csv_note", "report_csv_place", "report_col_type",
                        "report_col_start", "report_col_end", "report_col_minutes", "report_col_intensity_single", "report_col_scale", "report_col_details", "report_col_relief_single")
                        .map { context.t(it, emptyArray()) }
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(Report.csv(report, separator, headings) { v -> wellbeingWord(context, v) }.toByteArray()) }
                }.exceptionOrNull()
            }
            error?.let { snackbar.showSnackbar(context.getString(R.string.report_failed, it.message ?: "")) }
        }
    }

    SubPage(stringResource(R.string.report_title), onBack, backLabel = stringResource(R.string.nav_insights)) {
        Hint(stringResource(R.string.report_intro))
        FormCard {
            Label(stringResource(R.string.report_period_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf("1" to R.string.report_range_1m, "3" to R.string.report_range_3m, "12" to R.string.report_range_12m, "custom" to R.string.report_range_custom)) {
                    FilterChip(range == value, {
                        range = value
                        if (value != "custom") { from = today.minusMonths(value.toLong()).plusDays(1).toString(); to = today.toString() }
                    }, { Text(stringResource(label)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { range = "custom"; picking = "from" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.report_from) + ": " + fmt(from, "dMMMyyyy")) }
                OutlinedButton(onClick = { range = "custom"; picking = "to" }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.report_to) + ": " + fmt(to, "dMMMyyyy")) }
            }
            Label(stringResource(R.string.report_types))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(picked.isEmpty(), { picked = emptySet() }, { Text(stringResource(R.string.report_types_all)) })
                for (type in types.filter { !it.hidden || it.id in picked }) FilterChip(type.id in picked, { picked = if (type.id in picked) picked - type.id else picked + type.id },
                    { Text(localize(type.name)) }, leadingIcon = { EntryIcon(type, 18.dp) })
            }
        }
        Button(onClick = {
            if (from > to) return@Button
            scope.launch {
                val html = withContext(Dispatchers.IO) {
                    runCatching {
                        val r = build(context, diary, from, to, picked)
                        Report.html(r, Report.HtmlText({ k, a -> context.t(k, a) }, { fmt(it, "dMMMyyyy") }, { fmt(it, "EEEdMMMyyyy") }, { wellbeingWord(context, it) },
                            { number(it) }, { percent(it) }, Locale.getDefault().language, false, fmt(today.toString(), "dMMMyyyy")))
                    }
                }
                html.onSuccess {
                    print(context, it, "chronicals-report-$from-$to") {
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.report_failed, "WebView")) }
                    }
                }
                    .onFailure { snackbar.showSnackbar(context.getString(R.string.report_failed, it.message ?: "")) }
            }
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.report_print)) }
        OutlinedButton(onClick = { if (from <= to) saveCsv.launch(csvName) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.report_save_csv))
        }
    }
    picking?.let { which ->
        DateDialog(if (which == "from") from else to, onDismiss = { picking = null }) { if (which == "from") from = it else to = it; picking = null }
    }
}

// The WebView only lays out the report for printing and loads nothing from the internet. It is kept
// until the next report, since the print dialog reads from it while it is open.
private var printing: WebView? = null

/** The report through the system print dialog, which also offers "Save as PDF". */
private fun print(context: Context, html: String, name: String, onFailed: () -> Unit) {
    val web = WebView(context)
    web.settings.javaScriptEnabled = false
    web.webViewClient = object : WebViewClient() {
        // A crashed renderer would otherwise take the whole app with it (Android 8+; older ones end the app).
        @androidx.annotation.RequiresApi(26)
        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            if (printing === view) printing = null
            view.destroy()
            onFailed()
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            val manager = context.getSystemService(PrintManager::class.java)
            manager.print(name, view.createPrintDocumentAdapter(name), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        }
    }
    printing = web
    web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
