package io.github.stub1t.chronicals.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.BuildConfig
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Reminders
import io.github.stub1t.chronicals.data.Settings
import io.github.stub1t.chronicals.ui.day.TimeDialog
import java.time.LocalTime

/** What the Me screen asks of Android; the activity provides it. */
class MeActions(
    /** Turns reminders on, asking for the notification permission when needed; false when it was refused. */
    val applyReminders: (Reminders, (Boolean) -> Unit) -> Unit,
    /** Asks for the phone's lock and turns the app lock on; the result says what happened. */
    val setAppLock: (Boolean, (String?) -> Unit) -> Unit,
    /** Asks for the location permission; false when it was refused. */
    val setAutoLocation: (Boolean, (Boolean) -> Unit) -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    /** Android 13+: the system's per-app language screen. */
    val openLanguage: () -> Unit,
)

/** */
@Composable
fun MeScreen(vm: MeViewModel, settings: Settings, actions: MeActions, open: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    val timeFormat by settings.timeFormat.collectAsStateWithLifecycle()
    val reminders by settings.reminders.collectAsStateWithLifecycle()
    val appLock by settings.appLock.collectAsStateWithLifecycle()
    val autoLocation by settings.autoLocation.collectAsStateWithLifecycle()
    var reminderDenied by remember { mutableStateOf(false) }
    var appLockMessage by remember { mutableStateOf<String?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val profile = state.profile

    fun updateReminders(next: Reminders) = actions.applyReminders(next) { granted -> reminderDenied = next.daily && !granted }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        val name = profile?.displayName?.trim().orEmpty()
        Row(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)), RoundedCornerShape(24.dp),
            ).clickable { open("me/profile") }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                if (name.isNotEmpty()) Text(name.take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifEmpty { stringResource(R.string.me_add_name) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile?.locationName != null) {
                        Icon(Icons.Outlined.Place, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp)); Text(profile.locationName.substringBefore(","), style = MaterialTheme.typography.bodyMedium)
                    } else Text(stringResource(R.string.settings_profile_desc), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(R.string.entry_types_edit), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        Group(stringResource(R.string.me_health)) {
            val active = state.medications.count { it.isActive }
            MenuRow(Icons.Outlined.Medication, Color(0xFFE0679A), stringResource(R.string.settings_meds),
                if (active > 0) pluralStringResource(R.plurals.me_meds_active, active, active.toString()) else stringResource(R.string.me_meds_none)) { open("me/medications") }
            val hidden = state.types.count { it.hidden }
            MenuRow(Icons.Outlined.Category, Color(0xFF5B8DEF), stringResource(R.string.settings_entry_types),
                pluralStringResource(R.plurals.me_types_count, state.types.size, state.types.size.toString()) +
                    (if (hidden > 0) " · " + pluralStringResource(R.plurals.me_types_hidden, hidden, hidden.toString()) else "")) { open("me/entry-types") }
            MenuRow(Icons.Outlined.Bolt, Color(0xFFE3A13B), stringResource(R.string.settings_triggers),
                pluralStringResource(R.plurals.me_triggers_count, state.triggers.size, state.triggers.size.toString())) { open("me/triggers") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Group(stringResource(R.string.settings_online)) {
                val on = profile?.weatherEnabled == true
                Toggle(Icons.Outlined.WbCloudy, Color(0xFFE3A13B), stringResource(R.string.settings_weather),
                    stringResource(if (on) R.string.settings_weather_on else R.string.settings_weather_off), on) { next ->
                    vm.setWeather(next)
                    // Turning weather off also stops the automatic location.
                    if (!next && autoLocation) actions.setAutoLocation(false) {}
                }
                if (on) {
                    MenuRow(Icons.Outlined.Place, Color(0xFFD6455D), stringResource(R.string.settings_home_place),
                        profile.locationName?.substringBefore(",") ?: stringResource(R.string.settings_home_place_unset)) { open("me/profile") }
                    Toggle(Icons.Outlined.MyLocation, Color(0xFF3AA6B9), stringResource(R.string.settings_auto_location),
                        stringResource(R.string.settings_auto_location_note), autoLocation) { actions.setAutoLocation(it) {} }
                }
            }
            Hint(stringResource(R.string.settings_weather_note))
        }

        Group(stringResource(R.string.me_app)) {
            Toggle(Icons.Outlined.Notifications, Color(0xFF7462E8), stringResource(R.string.settings_reminder_daily),
                if (reminders.daily) stringResource(R.string.me_reminder_at, reminders.time) else null, reminders.daily) { updateReminders(reminders.copy(daily = it)) }
            if (reminders.daily) Sub {
                OutlinedButton(onClick = { pickTime = true }) { Text(stringResource(R.string.settings_reminder_time) + ": " + reminders.time) }
                Hint(stringResource(R.string.settings_reminder_daily_note))
                if (reminderDenied) Text(stringResource(R.string.settings_reminder_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Toggle(Icons.Outlined.Timer, Color(0xFFE3A13B), stringResource(R.string.settings_reminder_ongoing),
                if (reminders.ongoingHours > 0) pluralStringResource(R.plurals.settings_reminder_ongoing_after, reminders.ongoingHours, reminders.ongoingHours.toString()) else null,
                reminders.ongoingHours > 0) { updateReminders(reminders.copy(ongoingHours = if (it) 3 else 0)) }
            if (reminders.ongoingHours > 0) Sub {
                Segments(Settings.ONGOING_HOURS.map { it to pluralStringResource(R.plurals.settings_hours_short, it, it.toString()) }, reminders.ongoingHours) {
                    updateReminders(reminders.copy(ongoingHours = it))
                }
                Hint(stringResource(R.string.settings_reminder_ongoing_note))
            }
            Toggle(Icons.Outlined.Fingerprint, Color(0xFF2F9E6E), stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_toggle), appLock) {
                appLockMessage = null
                actions.setAppLock(it) { message -> appLockMessage = message }
            }
            if (appLock || appLockMessage != null) Sub {
                appLockMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Hint(stringResource(R.string.settings_app_lock_note))
            }
            MenuRow(Icons.Outlined.Palette, Color(0xFFD9822B), stringResource(R.string.settings_theme), null, null)
            Sub {
                Segments(listOf("system" to stringResource(R.string.settings_theme_system), "light" to stringResource(R.string.settings_theme_light),
                    "dark" to stringResource(R.string.settings_theme_dark)), theme, settings::setTheme)
            }
            MenuRow(Icons.Outlined.Schedule, Color(0xFF5B8DEF), stringResource(R.string.settings_time_format), null, null)
            Sub {
                Segments(listOf("auto" to stringResource(R.string.settings_time_auto), "24" to stringResource(R.string.settings_time_24),
                    "12" to stringResource(R.string.settings_time_12)), timeFormat, settings::setTimeFormat)
            }
            MenuRow(Icons.Outlined.Translate, Color(0xFF3AA6B9), stringResource(R.string.settings_language),
                java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }, actions.openLanguage)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Group(stringResource(R.string.settings_data)) {
                MenuRow(Icons.Outlined.Download, Color(0xFF5B8DEF), stringResource(R.string.settings_export), null, actions.exportBackup)
                MenuRow(Icons.Outlined.Upload, Color(0xFFD6455D), stringResource(R.string.settings_import), null, actions.importBackup)
            }
            Hint(stringResource(R.string.settings_data_note))
        }

        Group(stringResource(R.string.settings_about)) {
            MenuRow(Icons.Outlined.Info, MaterialTheme.colorScheme.onSurfaceVariant, "Chronicals ${BuildConfig.VERSION_NAME}", stringResource(R.string.settings_license), null)
            MenuRow(Icons.Outlined.Code, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.settings_source), null) { uri.openUri("https://github.com/stub1t/Chronicals") }
            MenuRow(Icons.Outlined.AutoAwesome, MaterialTheme.colorScheme.primary, stringResource(R.string.me_show_intro), null) { open("welcome") }
        }
    }

    if (pickTime) TimeDialog(LocalTime.parse(reminders.time), onDismiss = { pickTime = false }) {
        pickTime = false
        updateReminders(reminders.copy(time = "%02d:%02d".format(it.hour, it.minute)))
    }
}

@Composable
fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Sub(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun <T> Segments(options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            SegmentedButton(value == v, { onPick(v) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun Bubble(icon: ImageVector, color: Color) {
    Box(Modifier.size(36.dp).background(color.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun MenuRow(icon: ImageVector, color: Color, label: String, detail: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bubble(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(icon: ImageVector, color: Color, label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Bubble(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}

@Suppress("unused")
@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
