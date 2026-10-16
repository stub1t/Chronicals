package io.github.stub1t.chronicals.ui.me

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Json
import io.github.stub1t.chronicals.data.Trigger
import io.github.stub1t.chronicals.ui.CUSTOM_ICON_KEYS
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.LocalSnackbar
import io.github.stub1t.chronicals.ui.day.Label
import io.github.stub1t.chronicals.ui.day.RELIEF_KEY
import io.github.stub1t.chronicals.ui.day.categoryLabel
import io.github.stub1t.chronicals.ui.hexColor
import io.github.stub1t.chronicals.ui.localize
import kotlinx.coroutines.launch

/** A page under Me: back, title, an optional action, then the content. */
@Composable
fun SubPage(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}, backLabel: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, backLabel ?: stringResource(R.string.nav_me)) }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            action()
        }
        content()
    }
}

@Composable
fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

// ─── Profile ──────────────────────────────────────────────────────────────────

private val BLOOD_TYPES = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

/** Name, blood type and home place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePage(vm: MeViewModel, onBack: () -> Unit, placeSearch: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profile = state.profile ?: return
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember(state.loaded) { mutableStateOf(profile.displayName ?: "") }
    var blood by remember(state.loaded) { mutableStateOf(profile.bloodType) }
    SubPage(stringResource(R.string.profile_title), onBack) {
        FormCard {
            OutlinedTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.profile_display_name)) }, placeholder = { Text(stringResource(R.string.profile_display_name_placeholder)) },
                supportingText = { Text(stringResource(R.string.profile_display_name_hint)) })
            Label(stringResource(R.string.profile_blood_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(blood == null, { blood = null }, { Text(stringResource(R.string.profile_blood_type_unset)) })
                for (b in BLOOD_TYPES) FilterChip(blood == b, { blood = b }, { Text(b) })
            }
            Button(onClick = {
                vm.write { updateProfile(name, blood) }
                scope.launch { snackbar.showSnackbar(context.getString(R.string.profile_save_success)) }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
        FormCard {
            Label(stringResource(R.string.profile_location))
            if (!profile.weatherEnabled) Hint(stringResource(R.string.profile_location_off)) else placeSearch()
        }
    }
}

// ─── Triggers ─────────────────────────────────────────────────────────────────

private class Group(val value: String, val icon: ImageVector, val color: Color)
private val GROUPS = listOf(
    Group("food", Icons.Outlined.Restaurant, Color(0xFFD9822B)),
    Group("environment", Icons.Outlined.WbCloudy, Color(0xFF3AA6B9)),
    Group("activity", Icons.Outlined.DirectionsWalk, Color(0xFF7462E8)),
    Group("medication", Icons.Outlined.Medication, Color(0xFFE0679A)),
    Group("custom", Icons.Outlined.AutoAwesome, Color(0xFF94A3B8)),
)

/** The trigger library by group: rename, regroup, delete or bring back. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TriggersPage(vm: MeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Trigger?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val byName = compareBy<Trigger> { localize(it.name).lowercase() }

    SubPage(stringResource(R.string.triggers_title), onBack, action = {
        FilledTonalButton(onClick = { editing = null; sheet = "custom" }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.add_new)) }
    }) {
        Hint(stringResource(R.string.triggers_hint))
        for (g in GROUPS) FormCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(g.color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(g.icon, null, tint = g.color, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(categoryLabel(g.value)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in state.triggers.filter { it.category == g.value && !it.hidden }.sortedWith(byName)) InputChip(
                    selected = false, onClick = { editing = t; sheet = t.category }, label = { Text(localize(t.name)) },
                    trailingIcon = { Icon(Icons.Outlined.Edit, stringResource(R.string.triggers_edit, localize(t.name)), Modifier.size(14.dp)) },
                )
                InputChip(false, { editing = null; sheet = g.value }, { Icon(Icons.Outlined.Add, stringResource(R.string.triggers_add_to, stringResource(categoryLabel(g.value))), Modifier.size(16.dp)) })
            }
        }
        val hidden = state.triggers.filter { it.hidden }.sortedWith(byName)
        if (hidden.isNotEmpty()) FormCard {
            Text(pluralStringResource(R.plurals.triggers_deleted, hidden.size, hidden.size.toString()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Hint(stringResource(R.string.triggers_deleted_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in hidden) InputChip(false, { vm.write { restoreTrigger(t.id) } }, { Text(localize(t.name)) },
                    leadingIcon = { Icon(Icons.Outlined.Add, stringResource(R.string.triggers_restore, localize(t.name)), Modifier.size(14.dp)) })
            }
        }
    }

    sheet?.let { startGroup ->
        var name by remember(editing, startGroup) { mutableStateOf(editing?.let { localize(it.name) } ?: "") }
        var group by remember(editing, startGroup) { mutableStateOf(startGroup) }
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(editing?.let { localize(it.name) } ?: stringResource(R.string.triggers_new), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.entry_types_name)) }, placeholder = { Text(stringResource(R.string.triggers_new_placeholder)) })
                Label(stringResource(R.string.triggers_category))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (g in GROUPS) FilterChip(group == g.value, { group = g.value }, { Text(stringResource(categoryLabel(g.value))) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (editing != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    Button(modifier = Modifier.weight(1f), onClick = {
                        val typed = name.trim()
                        if (typed.isEmpty()) return@Button
                        val target = editing
                        // A built-in name shown in translation counts as unchanged.
                        val unchanged = target != null && typed == localize(target.name)
                        val same = if (unchanged) null else state.triggers.firstOrNull {
                            it.id != target?.id && (it.name.equals(typed, true) || localize(it.name).equals(typed, true))
                        }
                        // A deleted one is brought back by adding it again; anything else is a clash.
                        if (same != null && (target != null || !same.hidden)) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.triggers_exists)) }; return@Button
                        }
                        vm.write {
                            if (target != null) updateTrigger(target.id, if (unchanged) target.name else typed, group)
                            else createTrigger(same?.name ?: typed, group)
                        }
                        sheet = null
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.log_saved, typed)) }
                    }) { Text(stringResource(R.string.save)) }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
    if (confirmDelete) editing?.let { t ->
        AlertDialog(onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.triggers_delete_confirm, localize(t.name))) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; sheet = null; vm.write { deleteTrigger(t.id) } }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

// ─── Entry types ──────────────────────────────────────────────────────────────

val TYPE_COLORS = listOf("#e0679a", "#d6455d", "#d9822b", "#e3a13b", "#2f9e6e", "#3aa6b9", "#5b8def", "#7462e8", "#a855f7", "#94a3b8")

/** The metric schema of a new own type, as the web app wrote it. */
fun schemaFor(kind: String): String {
    val notes = mapOf("key" to "notes", "label" to "Notes", "type" to "textarea", "required" to false, "config" to mapOf("placeholder" to "Additional notes..."))
    val intensity = mapOf("key" to "intensity", "label" to "Intensity", "type" to "slider", "required" to true, "config" to mapOf("min" to 1, "max" to 5, "step" to 1))
    return Json.stringify(mapOf("fields" to if (kind == "symptom") listOf(intensity, notes) else listOf(notes)))
}

/** Every type with a switch for its tile on Today, and the editor. */
@Composable
fun EntryTypesPage(vm: MeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EntryType?>(null) }
    var open by remember { mutableStateOf(false) }
    SubPage(stringResource(R.string.entry_types_title), onBack, action = {
        FilledTonalButton(onClick = { editing = null; open = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text(stringResource(R.string.add_new)) }
    }) {
        Hint(stringResource(R.string.entry_types_hint))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Column {
                for (type in state.types.filter { it.category != "meal" }) Row(
                    Modifier.fillMaxWidth().clickable { editing = type; open = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EntryIcon(type, 40.dp, badge = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(localize(type.name), fontWeight = FontWeight.SemiBold)
                        Hint(if (type.isBuiltIn) stringResource(R.string.entry_types_builtin) else stringResource(when (type.category) { "event" -> R.string.entry_category_event; else -> R.string.entry_category_symptom }))
                    }
                    Switch(!type.hidden, { show -> vm.write { setTypeHidden(type.id, !show) } })
                }
            }
        }
    }
    if (open) EntryTypeSheet(vm.diary, editing, state.types.size) { open = false }
}

/** Create or edit a type: name, kind, colour and icon. Built-in types only drop options the user added. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EntryTypeSheet(diary: io.github.stub1t.chronicals.data.Diary, editing: EntryType?, colorIndex: Int, onCreated: (String) -> Unit = {}, onClose: () -> Unit) {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var icon by remember { mutableStateOf(editing?.icon ?: "lucide:activity") }
    var color by remember { mutableStateOf(editing?.color ?: TYPE_COLORS[colorIndex % TYPE_COLORS.size]) }
    var kind by remember { mutableStateOf("symptom") }
    var extra by remember { mutableStateOf(editing?.extraOptions ?: emptyMap()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val builtIn = editing?.isBuiltIn == true
    val preview = EntryType(editing?.id ?: "preview", name, icon, color, emptyList(), kind, 0, builtIn, false, emptyMap())

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntryIcon(preview, 56.dp, badge = true)
                Spacer(Modifier.width(14.dp))
                Text(editing?.let { localize(it.name) } ?: stringResource(R.string.entry_types_new), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (!builtIn) {
                OutlinedTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.entry_types_name)) }, placeholder = { Text(stringResource(R.string.entry_types_name_placeholder)) })
                if (editing == null) {
                    Label(stringResource(R.string.entry_types_kind))
                    Segments(listOf("symptom" to stringResource(R.string.entry_category_symptom), "event" to stringResource(R.string.entry_category_event)), kind) { kind = it }
                    Hint(stringResource(if (kind == "symptom") R.string.entry_types_kind_symptom else R.string.entry_types_kind_event))
                }
                Label(stringResource(R.string.entry_types_color))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in TYPE_COLORS) Box(
                        Modifier.size(36.dp).background(hexColor(c, Color.Gray), CircleShape)
                            .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { color = c },
                    )
                }
                Label(stringResource(R.string.entry_types_icon))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (key in CUSTOM_ICON_KEYS) {
                        val option = preview.copy(icon = "lucide:$key", id = "preview")
                        Surface(onClick = { icon = "lucide:$key" }, shape = RoundedCornerShape(12.dp),
                            border = if (icon == "lucide:$key") BorderStroke(2.dp, hexColor(color, Color.Gray)) else null,
                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { EntryIcon(option, 24.dp) }
                        }
                    }
                }
            }
            // Options the user added to fields (and to "Taken anything for it?") can be removed again.
            val fields = (editing?.fields.orEmpty().map { it.key to localize(it.label) } +
                (RELIEF_KEY to stringResource(if (editing?.id == "builtin-allergy") R.string.log_relief else R.string.log_painkillers)))
                .filter { (key, _) -> extra[key].orEmpty().isNotEmpty() }
            if (fields.isNotEmpty()) {
                Label(stringResource(R.string.entry_types_own_options))
                for ((key, label) in fields) Column {
                    Hint(label)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (option in extra[key].orEmpty()) InputChip(false, { extra = extra + (key to extra[key].orEmpty().minus(option)) }, { Text(option) },
                            trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.entry_types_remove_option, option), Modifier.size(14.dp)) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing != null && !builtIn) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                Button(modifier = Modifier.weight(1f), onClick = {
                    val typed = name.trim()
                    if (!builtIn && typed.isEmpty()) return@Button
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val d = diary
                            when {
                                builtIn -> { d.setExtraOptions(editing!!.id, extra); editing.id }
                                editing != null -> if (d.updateEntryType(editing.id, typed, icon, color, editingSchema(editing))) {
                                    d.setExtraOptions(editing.id, extra); editing.id } else null
                                else -> d.createEntryType(typed, icon, color, kind, schemaFor(kind))
                            }
                        }
                        if (ok == null) snackbar.showSnackbar(context.getString(R.string.entry_types_exists))
                        else {
                            onClose()
                            if (editing == null) onCreated(ok)
                            snackbar.showSnackbar(context.getString(R.string.log_saved, if (builtIn) localize(editing!!.name) else typed))
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        text = { Text(stringResource(R.string.entry_types_delete_confirm)) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            scope.launch {
                val deleted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { diary.deleteEntryType(editing!!.id) }
                if (deleted) onClose() else snackbar.showSnackbar(context.getString(R.string.entry_types_in_use))
            }
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}

/** An edited own type keeps its form fields. */
private fun editingSchema(type: EntryType): String = Json.stringify(mapOf("fields" to type.fields.map { f ->
    mapOf("key" to f.key, "label" to f.label, "type" to f.type, "required" to f.required, "config" to buildMap {
        if (f.type == "slider") { put("min", f.min); put("max", f.max); put("step", f.step) }
        if (f.options.isNotEmpty()) put("options", f.options)
        f.placeholder?.let { put("placeholder", it) }
        if (f.extendable) put("extendable", true)
        if (f.units.isNotEmpty()) put("units", f.units)
    })
}))
