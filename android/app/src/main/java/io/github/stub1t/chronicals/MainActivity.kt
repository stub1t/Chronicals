package io.github.stub1t.chronicals

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope

import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.platform.AppLock
import io.github.stub1t.chronicals.platform.PhoneLocation
import io.github.stub1t.chronicals.platform.WeatherSource
import io.github.stub1t.chronicals.ui.AppScaffold
import io.github.stub1t.chronicals.ui.LockScreen
import io.github.stub1t.chronicals.ui.OpenLog
import io.github.stub1t.chronicals.ui.PlaceSearch
import io.github.stub1t.chronicals.ui.clockFormat
import io.github.stub1t.chronicals.ui.me.MeActions
import io.github.stub1t.chronicals.ui.theme.ChronicalsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val app get() = application as ChronicalsApp
    private var locked by mutableStateOf(false)
    private var hiddenAt = 0L
    /** A tapped "still there?" notification: the log to open. */
    private var openLog by mutableStateOf<OpenLog?>(null)
    private var pending: ((Boolean) -> Unit)? = null
    private var pendingLock: ((Boolean) -> Unit)? = null

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted); pending = null
    }
    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingLock?.invoke(result.resultCode == RESULT_OK); pendingLock = null
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri -> uri?.let(::export) }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importUri = it } }
    private var importUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        locked = app.settings.appLock.value
        applySecure()
        handle(intent)
        val actions = MeActions(
            applyReminders = { next, done ->
                app.settings.setReminders(next)
                val wants = next.daily || next.ongoingHours > 0
                if (wants && Build.VERSION.SDK_INT >= 33 && !io.github.stub1t.chronicals.platform.Reminders.canNotify(this)) {
                    pending = { granted -> done(granted); syncReminders() }
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else { done(true); syncReminders() }
            },
            setAppLock = { on, done ->
                if (!on) { app.settings.setAppLock(false); applySecure(); done(null) }
                else if (!AppLock.available(this)) done(getString(R.string.settings_app_lock_unavailable))
                // Turned on after one successful unlock, so nobody locks themselves out by accident.
                else unlock { ok -> if (ok) { app.settings.setAppLock(true); applySecure() }; done(null) }
            },
            setAutoLocation = { on, done ->
                if (!on) { app.settings.setAutoLocation(false); done(true) }
                else if (PhoneLocation.allowed(this)) { app.settings.setAutoLocation(true); updatePlace(); done(true) }
                else {
                    pending = { granted -> if (granted) { app.settings.setAutoLocation(true); updatePlace() }; done(granted) }
                    askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            exportBackup = { exportFile.launch("chronicals-${Times.today()}.db") },
            // No type filter: Android's picker has no type for .db and would hide the file.
            importBackup = { importFile.launch(arrayOf("*/*")) },
            openLanguage = {
                if (Build.VERSION.SDK_INT >= 33) startActivity(Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null)))
                else startActivity(Intent(AndroidSettings.ACTION_LOCALE_SETTINGS))
            },
        )
        setContent {
            val theme by app.settings.theme.collectAsStateWithLifecycle()
            val timeFormat by app.settings.timeFormat.collectAsStateWithLifecycle()
            ChronicalsTheme(dark = when (theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }) {
                if (locked) LockScreen { unlock { ok -> if (ok) locked = false } }
                // Times are formatted while composing, so a new format redraws everything.
                else key(timeFormat) {
                    clockFormat = timeFormat
                    AppScaffold(app.diary, app.settings, actions, placeSearch = { PlaceSearch(app.diary) }, openLog = openLog, onLogOpened = { openLog = null },
                        importUri = importUri, onImport = ::restore, onImportCancelled = { importUri = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getStringExtra("kind") == "ongoing") {
            val date = intent.getStringExtra("date") ?: return
            val log = intent.getStringExtra("logId") ?: return
            openLog = OpenLog(LocalDate.parse(date), log)
        }
    }

    override fun onStart() {
        super.onStart()
        if (app.settings.appLock.value && hiddenAt > 0 && SystemClock.elapsedRealtime() - hiddenAt > AppLock.RELOCK_AFTER_MS) locked = true
        if (locked) unlock { ok -> if (ok) locked = false }
        updatePlace()
    }

    override fun onStop() {
        super.onStop()
        hiddenAt = SystemClock.elapsedRealtime()
    }

    private fun unlock(done: (Boolean) -> Unit) =
        AppLock.prompt(this, getString(R.string.app_lock_prompt), done) { intent -> pendingLock = done; confirmCredential.launch(intent) }

    /** While the lock is on, the app is hidden in the recent apps view and screenshots are blocked. */
    private fun applySecure() {
        if (app.settings.appLock.value) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun syncReminders() = lifecycleScope.launch(Dispatchers.IO) { io.github.stub1t.chronicals.platform.Reminders.sync(this@MainActivity) }

    /** Today's place when away (if on), then today's weather (if on). */
    private fun updatePlace() = lifecycleScope.launch(Dispatchers.IO) {
        PhoneLocation.updateTodaysPlace(this@MainActivity)
        WeatherSource.ensure(app.diary, listOf(Times.today().toString()))
    }

    private fun export(uri: Uri) = lifecycleScope.launch {
        val message = withContext(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)!!.use { app.diary.exportTo(it) }
                null
            } catch (e: Exception) { getString(R.string.settings_export_failed, e.message ?: "") }
        }
        message?.let { app.showMessage(it) }
    }

    private fun restore(uri: Uri) {
        importUri = null
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val days = app.diary.restoreFrom(contentResolver.openInputStream(uri)!!)
                    getString(R.string.settings_import_done, days.toString()).substringBefore(". ") + "."
                } catch (e: Exception) { getString(R.string.settings_import_failed, e.message ?: "") }
            }
            app.showMessage(message)
            syncReminders()
        }
    }
}
