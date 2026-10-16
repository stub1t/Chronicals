package io.github.stub1t.chronicals.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.EntryType
import io.github.stub1t.chronicals.data.Medication
import io.github.stub1t.chronicals.data.MedicationChange
import io.github.stub1t.chronicals.data.Profile
import io.github.stub1t.chronicals.data.Trigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeState(
    val loaded: Boolean = false,
    val profile: Profile? = null,
    val medications: List<Medication> = emptyList(),
    val changes: List<MedicationChange> = emptyList(),
    val types: List<EntryType> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
    /** Days each trigger was picked on, for the trigger list. */
    val triggerUse: Map<String, Int> = emptyMap(),
)

/** Profile, medications, entry types and triggers for Me and its pages. */
class MeViewModel(val diary: Diary) : ViewModel() {
    val state: StateFlow<MeState> = diary.changes.map {
        MeState(true, diary.profile(), diary.medications(), diary.medicationChanges(), diary.entryTypes(), diary.triggers())
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, MeState())

    fun write(block: Diary.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { diary.block() }
    }

    fun setWeather(on: Boolean) = write { setWeatherEnabled(on) }
}
