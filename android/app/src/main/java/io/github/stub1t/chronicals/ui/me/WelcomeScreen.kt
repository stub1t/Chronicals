package io.github.stub1t.chronicals.ui.me

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Settings
import io.github.stub1t.chronicals.ui.EntryIcon
import io.github.stub1t.chronicals.ui.localize

private const val STEPS = 5

/** The introduction on a fresh install: a few short steps, each saved as you go, all skippable. */
@Composable
fun WelcomeScreen(vm: MeViewModel, settings: Settings, actions: MeActions, placeSearch: @Composable () -> Unit, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reminders by settings.reminders.collectAsStateWithLifecycle()
    val appLock by settings.appLock.collectAsStateWithLifecycle()
    val autoLocation by settings.autoLocation.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var name by remember(state.loaded) { mutableStateOf(state.profile?.displayName ?: "") }
    val weatherOn = state.profile?.weatherEnabled == true

    fun finish() { settings.onboarded = true; onDone() }
    fun next() {
        if (step == 1) vm.write { updateProfile(name, state.profile?.bloodType) }
        if (step < STEPS - 1) step++ else finish()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) IconButton(onClick = { step-- }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.welcome_back)) }
            else Spacer(Modifier.width(48.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(STEPS) { i ->
                    Box(Modifier.padding(3.dp).size(if (i <= step) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            TextButton(onClick = ::finish) { Text(stringResource(R.string.welcome_skip)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                0 -> {
                    Image(painterResource(R.mipmap.ic_launcher_round), null, Modifier.size(96.dp).align(Alignment.CenterHorizontally))
                    Title(stringResource(R.string.welcome_title))
                    Lead(stringResource(R.string.welcome_text))
                    OutlinedButton(onClick = actions.openLanguage, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Outlined.Translate, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_language) + ": " + java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() })
                    }
                }
                1 -> {
                    Title(stringResource(R.string.welcome_name_title))
                    Lead(stringResource(R.string.profile_display_name_hint))
                    OutlinedTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(stringResource(R.string.profile_display_name_placeholder)) })
                }
                2 -> {
                    Title(stringResource(R.string.welcome_types_title))
                    Lead(stringResource(R.string.welcome_types_text))
                    // Meals have their own tab, so they are not switched on or off here.
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Column {
                            for (type in state.types.filter { it.isBuiltIn && it.category != "meal" }) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                EntryIcon(type, 40.dp, badge = true)
                                Spacer(Modifier.width(12.dp))
                                Text(localize(type.name), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Switch(!type.hidden, { on -> vm.write { setTypeHidden(type.id, !on) } })
                            }
                        }
                    }
                    Hint(stringResource(R.string.welcome_types_hint))
                }
                3 -> {
                    Title(stringResource(R.string.welcome_place_title))
                    Lead(stringResource(R.string.settings_weather_note))
                    SwitchRow(stringResource(R.string.settings_weather), stringResource(if (weatherOn) R.string.settings_weather_on else R.string.settings_weather_off), weatherOn) { on ->
                        vm.setWeather(on)
                        if (!on && autoLocation) actions.setAutoLocation(false) {}
                    }
                    if (weatherOn) {
                        Text(stringResource(R.string.welcome_place_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        placeSearch()
                        SwitchRow(stringResource(R.string.settings_auto_location), stringResource(R.string.settings_auto_location_note), autoLocation) { actions.setAutoLocation(it) {} }
                    }
                }
                else -> {
                    Title(stringResource(R.string.welcome_reminders_title))
                    Lead(stringResource(R.string.welcome_reminders_text))
                    SwitchRow(stringResource(R.string.settings_reminder_daily), stringResource(R.string.me_reminder_at, reminders.time), reminders.daily) {
                        actions.applyReminders(reminders.copy(daily = it)) {}
                    }
                    SwitchRow(stringResource(R.string.settings_reminder_ongoing), pluralStringResource(R.plurals.settings_reminder_ongoing_after, 3, "3"), reminders.ongoingHours > 0) {
                        actions.applyReminders(reminders.copy(ongoingHours = if (it) 3 else 0)) {}
                    }
                    SwitchRow(stringResource(R.string.settings_app_lock), stringResource(R.string.settings_app_lock_toggle), appLock) { actions.setAppLock(it) {} }
                }
            }
        }
        Button(onClick = ::next, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(52.dp)) {
            Text(stringResource(if (step == STEPS - 1) R.string.welcome_done else R.string.welcome_next), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@Composable
private fun Lead(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

@Composable
private fun SwitchRow(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, onClick = { onChange(!checked) }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                description?.let { Hint(it) }
            }
            Switch(checked, onChange)
        }
    }
}
