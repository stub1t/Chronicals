package io.github.stub1t.chronicals.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.platform.WeatherSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A log to open from a tapped "still there?" notification. */
data class OpenLog(val date: LocalDate, val logId: String)

/** Shown while the app lock is on and not yet unlocked. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_lock_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock) { Text(stringResource(R.string.app_lock_unlock)) }
        }
    }
}

/** The home place: search Open-Meteo's place names (only with weather on) or clear it. Port of src/lib/me/LocationSearch.svelte. */
@Composable
fun PlaceSearch(diary: Diary) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherSource.Place>>(emptyList()) }
    var home by remember { mutableStateOf(diary.profile().locationName) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        home?.let { name ->
            ListItem(headlineContent = { Text(name) }, leadingContent = { Icon(Icons.Outlined.Place, null) },
                trailingContent = { TextButton(onClick = { scope.launch(Dispatchers.IO) { diary.setHome(null, null, null) }; home = null }) { Text(stringResource(R.string.profile_location_clear)) } })
        }
        Text(stringResource(R.string.profile_location_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(text, { if (it.length <= 120) text = it }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(stringResource(R.string.profile_location_search)) },
            trailingIcon = {
                TextButton(onClick = { scope.launch { results = withContext(Dispatchers.IO) { WeatherSource.search(diary, text) } } }) {
                    Icon(Icons.Outlined.Search, stringResource(R.string.profile_location_search_button))
                }
            })
        for (place in results) ListItem(
            headlineContent = { Text(place.name.substringBefore(",")) },
            supportingContent = { Text(place.name.substringAfter(", ", "")) },
            modifier = Modifier.clickable {
                scope.launch(Dispatchers.IO) { diary.setHome(place.lat, place.lon, place.name) }
                home = place.name; results = emptyList(); text = ""
            },
        )
    }
}
