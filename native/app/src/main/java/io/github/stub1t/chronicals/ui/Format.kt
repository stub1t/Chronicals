package io.github.stub1t.chronicals.ui

import android.content.Context
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Times
import java.text.NumberFormat
import java.util.Date

/** Clock time in the phone's 12 or 24 hour setting. */
fun clock(context: Context, iso: String): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date.from(Times.parse(iso)))

fun number(value: Double): String = NumberFormat.getInstance().apply { maximumFractionDigits = 1 }.format(value)

/** "15 min" below an hour, else hours in halves ("1.5 hours"). */
fun duration(context: Context, minutes: Long, roundDown: Boolean = false): String {
    val res = context.resources
    if (minutes < 60) return res.getQuantityString(R.plurals.duration_minutes, minutes.toInt(), minutes.toString())
    val halves = if (roundDown) minutes / 30 else Math.round(minutes / 30.0)
    val hours = halves / 2.0
    return res.getQuantityString(R.plurals.duration_hours, if (hours == 1.0) 1 else 2, number(hours))
}

/** A built-in name or option in the app language; the user's own text as typed. */
fun localize(text: String?): String = BuiltInText.localize(text)
