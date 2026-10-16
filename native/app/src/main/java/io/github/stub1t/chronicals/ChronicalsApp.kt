package io.github.stub1t.chronicals

import android.app.Application
import io.github.stub1t.chronicals.data.Diary

class ChronicalsApp : Application() {
    /** Opened on first use; the start screen covers the few milliseconds it takes. */
    val diary: Diary by lazy { Diary(this) }
}
