package io.github.stub1t.chronicals.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Times are stored as JavaScript's toISOString() writes them (UTC, milliseconds, Z), so both apps sort them the same. */
object Times {
    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun iso(instant: Instant): String = ISO.format(instant)
    fun isoNow(): String = iso(Instant.now())
    fun parse(iso: String): Instant = Instant.parse(iso)
    fun zone(): ZoneId = ZoneId.systemDefault()
    fun local(iso: String): ZonedDateTime = parse(iso).atZone(zone())
    fun today(): LocalDate = LocalDate.now(zone())
    /** Start of a day in the phone's time zone, as a stored ISO string. */
    fun startOfDay(date: LocalDate): String = iso(date.atStartOfDay(zone()).toInstant())
}
