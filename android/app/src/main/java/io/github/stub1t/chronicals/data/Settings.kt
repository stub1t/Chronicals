package io.github.stub1t.chronicals.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Daily reminder at a time; "still going?" after some hours (0: off). */
data class Reminders(val daily: Boolean = false, val time: String = "20:00", val ongoingHours: Int = 3)

/**
 * Settings of this phone, kept in SharedPreferences (the Capacitor app kept them in the WebView's
 * localStorage, which the native app cannot read, so they start at their defaults once).
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** system, light or dark */
    private val _theme = MutableStateFlow(prefs.getString("theme", "system")!!)
    val theme: StateFlow<String> = _theme.asStateFlow()
    fun setTheme(value: String) { prefs.edit().putString("theme", value).apply(); _theme.value = value }

    /** auto (the phone's setting), 24 or 12 */
    private val _timeFormat = MutableStateFlow(prefs.getString("time-format", "auto")!!)
    val timeFormat: StateFlow<String> = _timeFormat.asStateFlow()
    fun setTimeFormat(value: String) { prefs.edit().putString("time-format", value).apply(); _timeFormat.value = value }

    private val _reminders = MutableStateFlow(Reminders(
        prefs.getBoolean("reminder-daily", false),
        prefs.getString("reminder-time", "20:00")!!,
        prefs.getInt("reminder-ongoing-hours", 3),
    ))
    val reminders: StateFlow<Reminders> = _reminders.asStateFlow()
    fun setReminders(value: Reminders) {
        prefs.edit().putBoolean("reminder-daily", value.daily).putString("reminder-time", value.time).putInt("reminder-ongoing-hours", value.ongoingHours).apply()
        _reminders.value = value
    }

    private val _appLock = MutableStateFlow(prefs.getBoolean("app-lock", false))
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()
    fun setAppLock(on: Boolean) { prefs.edit().putBoolean("app-lock", on).apply(); _appLock.value = on }

    /** The day's place follows the phone's location (needs weather on). */
    private val _autoLocation = MutableStateFlow(prefs.getBoolean("auto-location", false))
    val autoLocation: StateFlow<Boolean> = _autoLocation.asStateFlow()
    fun setAutoLocation(on: Boolean) { prefs.edit().putBoolean("auto-location", on).apply(); _autoLocation.value = on }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit().putBoolean("onboarded", value).apply()

    companion object {
        val ONGOING_HOURS = listOf(1, 2, 3, 4, 6)
    }
}
