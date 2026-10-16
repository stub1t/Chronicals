package io.github.stub1t.chronicals

import android.app.Application
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Settings
import io.github.stub1t.chronicals.platform.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

class ChronicalsApp : Application() {
    /** Opened on first use; the start screen covers the few milliseconds it takes. */
    val diary: Diary by lazy { Diary(this) }
    val settings: Settings by lazy { Settings(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Short messages from outside a screen (a backup saved, say), shown by the scaffold. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    fun showMessage(text: String) { _messages.trySend(text) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Medications, logs still going and the settings decide which reminders ring: plan them again after changes.
        combine(diary.changes, settings.reminders) { _, _ -> }.debounce(500).onEach { Reminders.sync(this) }.launchIn(scope)
    }
}
