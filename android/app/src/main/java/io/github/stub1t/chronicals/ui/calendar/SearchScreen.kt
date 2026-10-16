package io.github.stub1t.chronicals.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Search
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.localize
import io.github.stub1t.chronicals.ui.typeColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Days by text, type and period; matching happens on the phone, so translated names are found too. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(diary: Diary, onBack: () -> Unit, openDay: (LocalDate) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var typeId by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf("12m") }
    val today = Times.today()
    val from = when (period) { "3m" -> today.minusMonths(3); "12m" -> today.minusMonths(12); else -> LocalDate.of(1900, 1, 1) }
    val vm: RangeViewModel = viewModel(key = "search") { RangeViewModel(diary, from to today) }
    LaunchedEffect(from) { vm.range.value = from to today }
    val state by vm.state.collectAsStateWithLifecycle()
    val names = state.types.mapValues { it.value.name }
    val active = query.isNotBlank() || typeId != null
    val results = if (!active) emptyList() else state.entries.values
        .filter { Search.matches(it, query, typeId, names, ::localize) }.sortedByDescending { it.date }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val dayFormat = DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMMyyyy"))

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.calendar_back)) }
                Text(stringResource(R.string.search_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(R.string.search_placeholder)) })
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(typeId == null, { typeId = null }, { Text(stringResource(R.string.search_all_types)) })
                for (type in state.types.values.sortedBy { it.sortOrder }) FilterChip(typeId == type.id, { typeId = if (typeId == type.id) null else type.id },
                    { Text(localize(type.name)) }, leadingIcon = { EntryIcon(type, 18.dp) })
            }
        }
        item {
            val options = listOf("3m" to R.string.search_period_3m, "12m" to R.string.search_period_12m, "all" to R.string.search_period_all)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(period == value, { period = value }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(stringResource(label)) }
                }
            }
        }
        if (!active) item {
            Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
        } else if (state.loaded) {
            item {
                Text(if (results.isEmpty()) stringResource(R.string.search_no_results) else stringResource(R.string.search_results, results.size.toString()),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(results, key = { it.id }) { entry ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth().clickable { openDay(LocalDate.parse(entry.date)) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(LocalDate.parse(entry.date).format(dayFormat), fontWeight = FontWeight.Bold)
                        if (entry.logs.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (log in entry.logs) {
                                val type = state.types[log.entryTypeId]
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EntryIcon(type, 18.dp); Spacer(Modifier.width(4.dp))
                                    Text(localize(type?.name), color = typeColor(type), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (entry.triggers.isNotEmpty()) Text(entry.triggers.joinToString(" · ") { localize(it.name) },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        entry.note?.takeIf { it.isNotBlank() }?.let {
                            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.padding(16.dp)) }
    }
}
