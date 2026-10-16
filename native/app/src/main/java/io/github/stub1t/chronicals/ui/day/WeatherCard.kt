package io.github.stub1t.chronicals.ui.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Insights
import io.github.stub1t.chronicals.platform.PhoneLocation
import io.github.stub1t.chronicals.platform.WeatherSource
import io.github.stub1t.chronicals.ui.LocalSnackbar
import io.github.stub1t.chronicals.ui.number
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

private val Warn = Color(0xFFE3A13B)

private val CONDITIONS = mapOf(
    "clear sky" to (Icons.Outlined.WbSunny to R.string.env_weather_clear_sky),
    "partly cloudy" to (Icons.Outlined.WbCloudy to R.string.env_weather_partly_cloudy),
    "overcast" to (Icons.Outlined.Cloud to R.string.env_weather_overcast),
    "foggy" to (Icons.Outlined.Dehaze to R.string.env_weather_foggy),
    "drizzle" to (Icons.Outlined.Grain to R.string.env_weather_drizzle),
    "rain" to (Icons.Outlined.Umbrella to R.string.env_weather_rain),
    "rain showers" to (Icons.Outlined.Umbrella to R.string.env_weather_rain_showers),
    "snow" to (Icons.Outlined.AcUnit to R.string.env_weather_snow),
    "snow showers" to (Icons.Outlined.AcUnit to R.string.env_weather_snow_showers),
    "thunderstorm" to (Icons.Outlined.Thunderstorm to R.string.env_weather_thunderstorm),
)

private fun pollenName(s: String) = when (s) {
    "alder" -> R.string.pollen_alder; "birch" -> R.string.pollen_birch; "grass" -> R.string.pollen_grass
    "mugwort" -> R.string.pollen_mugwort; "olive" -> R.string.pollen_olive; else -> R.string.pollen_ragweed
}

/**
 * The day's place, weather and pollen. The place is home unless the day has its own (travel), which can
 * be changed here. Off: only a pointer to the setting. Port of src/lib/diary/WeatherCard.svelte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherCard(state: DayState, vm: DayViewModel, openSettings: () -> Unit) {
    val profile = state.profile ?: return
    val entry = state.entry
    var sheet by remember { mutableStateOf(false) }
    val own = if (entry?.locationLat != null && entry.locationLon != null) entry.locationLat to entry.locationLon else null
    val home = if (profile.locationLat != null && profile.locationLon != null) profile.locationLat to profile.locationLon else null
    val place = own ?: home
    LaunchedEffect(profile.weatherEnabled, place) { if (profile.weatherEnabled && place != null) vm.loadWeather() }

    val shape = RoundedCornerShape(18.dp)
    when {
        !profile.weatherEnabled -> Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WbCloudy, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.today_weather_off), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = openSettings) { Text(stringResource(R.string.today_weather_turn_on)) }
            }
        }
        place == null -> Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.today_weather_needs_location), Modifier.weight(1f))
                TextButton(onClick = { sheet = true }) { Text(stringResource(R.string.place_change)) }
            }
        }
        else -> {
            val w = state.weather
            val bigDrop = (w?.pressureDeltaHpa ?: 0.0) <= -5
            Surface(shape = shape, color = if (bigDrop) Warn.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        val name = if (own != null) entry?.locationName ?: stringResource(R.string.place_away) else profile.locationName?.substringBefore(",") ?: ""
                        Spacer(Modifier.width(4.dp))
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry?.locationSource == "auto") {
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.place_auto), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { sheet = true }) { Text(stringResource(R.string.place_change)) }
                    }
                    if (w != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val (icon, label) = CONDITIONS[w.condition] ?: (Icons.Outlined.Cloud to R.string.env_weather_unknown)
                            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                w.temperatureC?.let { Text("${it.roundToInt()}°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                                if (w.condition != null) Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                            }
                            w.airPressureHpa?.let { p ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${p.roundToInt()} hPa", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    w.pressureDeltaHpa?.let { d ->
                                        Text((if (d < 0) "↓ " else "↑ ") + number(abs(d)), style = MaterialTheme.typography.labelMedium,
                                            color = if (bigDrop) Warn else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        // Every measured pollen, the highest first.
                        val rank = mapOf("high" to 0, "moderate" to 1, "low" to 2)
                        val pollen = Insights.POLLEN.mapNotNull { s -> Insights.pollenLevel(s, w.pollen[s])?.let { s to it } }.sortedBy { rank[it.second] }
                        if (pollen.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.LocalFlorist, null, Modifier.size(16.dp))
                            for ((s, level) in pollen) {
                                val c = when (level) { "high" -> MaterialTheme.colorScheme.error; "moderate" -> Warn; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                                Text(stringResource(pollenName(s)) + " " + stringResource(when (level) { "high" -> R.string.pollen_high; "moderate" -> R.string.pollen_moderate; else -> R.string.pollen_low }),
                                    style = MaterialTheme.typography.labelMedium, color = c,
                                    modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    } else Text(stringResource(R.string.today_weather_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (bigDrop) Text(stringResource(R.string.today_pressure_drop), style = MaterialTheme.typography.bodySmall, color = Warn, modifier = Modifier.padding(top = 6.dp))
        }
    }
    if (sheet) PlaceSheet(vm, profile.locationName?.let { n -> home?.let { n } }, own == null) { sheet = false }
}

/** Where the day was spent: home, here, or a searched place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceSheet(vm: DayViewModel, homeName: String?, atHome: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherSource.Place>>(emptyList()) }
    val date = vm.date.toString()
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.place_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.place_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (homeName != null) Choice(Icons.Outlined.Home, stringResource(R.string.place_home, homeName.substringBefore(",")), atHome) {
                vm.write { setDayPlace(date, null, null, null, null) }; onClose()
            }
            Choice(Icons.Outlined.MyLocation, stringResource(if (locating) R.string.place_locating else R.string.place_current), false) {
                if (locating) return@Choice
                locating = true
                scope.launch {
                    val here = withContext(Dispatchers.IO) { PhoneLocation.current(context) }
                    locating = false
                    if (here == null) snackbar.showSnackbar(context.getString(R.string.place_not_found))
                    else { vm.write { setDayPlace(date, round(here.first * 100) / 100, round(here.second * 100) / 100, null, "manual") }; onClose() }
                }
            }
            Text(stringResource(R.string.place_other), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(text, { if (it.length <= 120) text = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(stringResource(R.string.profile_location_search)) },
                trailingIcon = { TextButton(onClick = { scope.launch { results = withContext(Dispatchers.IO) { WeatherSource.search(vm.diary, text) } } }) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.profile_location_search_button)) } })
            for (p in results) Choice(Icons.Outlined.Place, p.name, false) {
                vm.write { setDayPlace(date, p.lat, p.lon, p.name, "manual") }; onClose()
            }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, label: String, current: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}
