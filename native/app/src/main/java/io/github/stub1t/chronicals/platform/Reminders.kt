package io.github.stub1t.chronicals.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.stub1t.chronicals.ChronicalsApp
import io.github.stub1t.chronicals.MainActivity
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.BuiltInText
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Daily reminder, medication times and "still there?" for symptoms that are still going. Alarms are
 * inexact (no exact-alarm permission, like the Capacitor app), so a reminder can come a few minutes late.
 * Each alarm shows its notification and plans the next one. Port of src/lib/reminders.ts and mobile/reminders.ts.
 */
object Reminders {
    private const val CHANNEL = "reminders"
    private const val DAILY_ID = 1
    private const val PREFS = "reminder-alarms"

    /** Stable positive id per key (FNV-1a), never the daily reminder's id. */
    fun idOf(key: String): Int {
        var hash = 0x811c9dc5.toInt()
        for (c in key) { hash = hash xor c.code; hash *= 0x01000193 }
        return ((hash.toLong() and 0xffffffffL) % 2_000_000_000L).toInt() + DAILY_ID + 1
    }

    class Planned(val id: Int, val title: String, val body: String, val at: Instant, val kind: String, val date: String? = null, val logId: String? = null)

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Everything that should ring next, from the settings, the active medications and logs still going. */
    fun plan(context: Context, now: ZonedDateTime = ZonedDateTime.now()): List<Planned> {
        val app = context.applicationContext as ChronicalsApp
        val settings = app.settings.reminders.value
        val planned = mutableListOf<Planned>()
        fun next(time: String): Instant {
            val t = LocalTime.parse(time)
            var at = now.toLocalDate().atTime(t).atZone(now.zone)
            if (!at.isAfter(now)) at = at.plusDays(1)
            return at.toInstant()
        }
        if (settings.daily) planned += Planned(DAILY_ID, context.getString(R.string.reminder_daily_title), context.getString(R.string.reminder_daily_body), next(settings.time), "daily")
        for (med in app.diary.medications().filter { it.isActive }) {
            val dosage = listOfNotNull(med.dosage, med.unit).filter { it.isNotEmpty() }.joinToString(" ")
            for (time in med.reminderTimes) planned += Planned(idOf("${med.id}|$time"), med.name,
                context.getString(R.string.reminder_medication_body, listOf(med.name, dosage).filter { it.isNotEmpty() }.joinToString(" ")), next(time), "medication")
        }
        if (settings.ongoingHours > 0) {
            val types = app.diary.entryTypes().associateBy { it.id }
            val today = now.toLocalDate()
            for (entry in app.diary.entriesBetween(today.minusDays(1).toString(), today.toString())) for (log in entry.logs) {
                val category = types[log.entryTypeId]?.category
                if (log.endTime != null || (category != "symptom" && category != "event")) continue
                val start = Times.parse(log.startTime)
                val at = start.plusSeconds(settings.ongoingHours * 3600L)
                // Logs that started more than a day ago are left alone.
                if (!at.isAfter(now.toInstant()) || now.toInstant().epochSecond - start.epochSecond > 86_400) continue
                val name = BuiltInText.localize(types[log.entryTypeId]?.name)
                planned += Planned(idOf("ongoing|${log.id}"), context.getString(R.string.reminder_ongoing_title, name),
                    context.getString(R.string.reminder_ongoing_body), at, "ongoing", entry.date, log.id)
            }
        }
        return planned
    }

    /** Replaces every scheduled alarm with the current plan. Call after settings, medications or logs change. */
    fun sync(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (id in prefs.getStringSet("ids", emptySet())!!) alarms.cancel(pending(context, id.toInt(), null))
        val planned = plan(context)
        for (p in planned) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at.toEpochMilli(), pending(context, p.id, p))
        prefs.edit().putStringSet("ids", planned.map { it.id.toString() }.toSet()).apply()
    }

    private fun pending(context: Context, id: Int, p: Planned?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("reminder-$id")
        if (p != null) intent.putExtra("id", p.id).putExtra("title", p.title).putExtra("body", p.body).putExtra("kind", p.kind)
            .putExtra("date", p.date).putExtra("logId", p.logId)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, intent: Intent) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.settings_reminders), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val id = intent.getIntExtra("id", DAILY_ID)
        // A tap opens the app; "still there?" opens that log so the end can be entered.
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("kind", intent.getStringExtra("kind")).putExtra("date", intent.getStringExtra("date")).putExtra("logId", intent.getStringExtra("logId"))
        val tap = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_chronicals)
            .setContentTitle(intent.getStringExtra("title"))
            .setContentText(intent.getStringExtra("body"))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, notification) } catch (_: SecurityException) { }
    }
}

/** Shows a due reminder and plans the next ones; also runs after a restart, since alarms do not survive one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try {
                if (intent.action?.startsWith("reminder-") == true) Reminders.show(context, intent)
                Reminders.sync(context)
            } finally {
                result.finish()
            }
        }.start()
    }
}
