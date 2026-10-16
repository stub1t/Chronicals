package io.github.stub1t.chronicals.ui.day

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Field
import io.github.stub1t.chronicals.ui.localize

/**
 * The form fields of an entry type, made quick to fill on a phone: 1 to 5 scales become five big
 * buttons, short option lists chips, longer ones a menu. Port of src/lib/diary/LogFields.svelte.
 */
@Composable
fun LogFields(
    type: EntryType,
    values: Map<String, Any?>,
    color: Color,
    onChange: (String, Any?) -> Unit,
    onAddOption: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        for (field in type.fields) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(localize(field.label))
                val options = type.optionsFor(field)
                when {
                    field.type == "slider" && field.min == 1 && field.max == 5 -> Scale((values[field.key] as? Number)?.toInt() ?: 0, color) { onChange(field.key, it) }
                    field.type == "slider" -> {
                        val value = (values[field.key] as? Number)?.toFloat() ?: ((field.min + field.max) / 2f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = value,
                                onValueChange = { onChange(field.key, Math.round(it)) },
                                valueRange = field.min.toFloat()..field.max.toFloat(),
                                steps = ((field.max - field.min) / field.step.coerceAtLeast(1) - 1).coerceAtLeast(0),
                                modifier = Modifier.weight(1f),
                            )
                            Text(Math.round(value).toString(), Modifier.width(40.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    field.type == "select" && options.size <= 12 -> Chips(field, options, color, picked = { values[field.key] == it }, onAddOption = onAddOption,
                        onPick = { onChange(field.key, if (values[field.key] == it) null else it) },
                        onAdded = { onChange(field.key, it) })
                    field.type == "select" -> Dropdown(options, values[field.key] as? String) { onChange(field.key, it) }
                    field.type == "checklist" -> {
                        val current = (values[field.key] as? List<*>)?.map { it.toString() } ?: emptyList()
                        Chips(field, options, color, picked = { it in current }, onAddOption = onAddOption,
                            onPick = { onChange(field.key, if (it in current) current - it else current + it) },
                            onAdded = { if (it !in current) onChange(field.key, current + it) })
                    }
                    field.type == "number" -> NumberField(field, values, onChange)
                    else -> {
                        val text = values[field.key]?.toString() ?: ""
                        OutlinedTextField(
                            value = text,
                            onValueChange = { onChange(field.key, it) },
                            placeholder = { Text(localize(field.placeholder)) },
                            minLines = if (field.type == "textarea") 3 else 1,
                            singleLine = field.type != "textarea",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun scaleWord(context: Context, level: Int): String = context.getString(
    when (level) { 1 -> R.string.intensity_1; 2 -> R.string.intensity_2; 3 -> R.string.intensity_3; 4 -> R.string.intensity_4; else -> R.string.intensity_5 }
)

/** Five big buttons; the ones up to the picked level fill in. */
@Composable
private fun Scale(value: Int, color: Color, onPick: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            for (level in 1..5) {
                val on = value >= level
                Surface(
                    onClick = { onPick(level); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (on) color.copy(alpha = 0.25f + level * 0.15f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(if (value == level) 2.dp else 1.dp, if (on) color else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("$level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = if (on && level >= 3) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        Text(if (value in 1..5) scaleWord(context, value) else " ", color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding4())
    }
}

private fun Modifier.padding4() = this.then(Modifier.height(28.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(
    field: Field?,
    options: List<String>,
    color: Color,
    picked: (String) -> Boolean,
    onPick: (String) -> Unit,
    onAddOption: ((String, String) -> Unit)?,
    onAdded: (String) -> Unit,
    addLabel: String? = null,
    /** Form options are stored in lower case and start with a capital on screen. */
    capitalize: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    var adding by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        for (option in options) {
            FilterChip(
                selected = picked(option),
                onClick = { onPick(option); haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                label = { Text(localize(option).let { if (capitalize) it.replaceFirstChar { c -> c.titlecase() } else it }) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.2f)),
            )
        }
        if (onAddOption != null && (field == null || field.extendable)) {
            if (adding) {
                AddOption(addLabel ?: stringResource(R.string.log_own_option)) { typed ->
                    adding = false
                    if (typed.isEmpty()) return@AddOption
                    // An option with that name, also under its translated name, is picked instead of added twice.
                    val known = options.firstOrNull { it.equals(typed, true) || localize(it).equals(typed, true) }
                    if (known == null) onAddOption(field?.key ?: "", typed)
                    onAdded(known ?: typed)
                }
            } else {
                FilterChip(selected = false, onClick = { adding = true }, label = { Text("+ " + stringResource(R.string.add_new)) })
            }
        }
    }
}

/** A small field for an own option; done on enter or when it loses focus. */
@Composable
private fun AddOption(placeholder: String, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= 80) text = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone(text.trim()) }),
        modifier = Modifier
            .width(200.dp)
            .focusRequester(focus)
            .onFocusChanged { if (focused && !it.isFocused) onDone(text.trim()); focused = it.isFocused },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(options: List<String>, value: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value?.let { localize(it).replaceFirstChar { c -> c.titlecase() } } ?: stringResource(R.string.metric_choose),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.metric_choose)) }, onClick = { onPick(null); open = false })
            for (option in options) DropdownMenuItem(text = { Text(localize(option).replaceFirstChar { it.titlecase() }) }, onClick = { onPick(option); open = false })
        }
    }
}

/** A number with its unit; the unit picked last is kept per field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberField(field: Field, values: Map<String, Any?>, onChange: (String, Any?) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("units", Context.MODE_PRIVATE) }
    val unit = values["${field.key}_unit"] as? String ?: prefs.getString(field.key, null) ?: field.units.firstOrNull()
    var text by remember { mutableStateOf(values[field.key]?.let { (it as? Number)?.let { n -> io.github.stub1t.chronicals.ui.number(n.toDouble()) } ?: it.toString() } ?: "") }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed
                val parsed = typed.replace(',', '.').toDoubleOrNull()
                onChange(field.key, parsed?.let { if (it % 1.0 == 0.0) it.toLong() else it })
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.width(140.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in field.units) {
                FilterChip(selected = unit == option, onClick = {
                    onChange("${field.key}_unit", option)
                    prefs.edit().putString(field.key, option).apply()
                }, label = { Text(option) })
            }
        }
    }
}
