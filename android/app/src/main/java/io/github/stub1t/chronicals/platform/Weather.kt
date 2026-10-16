package io.github.stub1t.chronicals.platform

import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.data.Weather
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

/**
 * Weather and pollen per day from Open-Meteo, for where the day was spent (its own place, or home).
 * Nothing is sent while weather is off.
 */
object WeatherSource {
    private val SPECIES = listOf("alder", "birch", "grass", "mugwort", "olive", "ragweed")

    /** About 5 km: closer places share their weather. */
    private fun samePlace(a: Pair<Double, Double>, b: Pair<Double, Double>) = abs(a.first - b.first) < 0.05 && abs(a.second - b.second) < 0.05

    private fun get(url: String, timeout: Int = 10_000): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Open-Meteo answered ${connection.responseCode}")
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun q(vararg params: Pair<String, String>) = params.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }

    private fun condition(code: Int) = when {
        code == 0 -> "clear sky"; code <= 2 -> "partly cloudy"; code == 3 -> "overcast"; code <= 49 -> "foggy"; code <= 59 -> "drizzle"
        code <= 69 -> "rain"; code <= 79 -> "snow"; code <= 82 -> "rain showers"; code <= 86 -> "snow showers"; code <= 99 -> "thunderstorm"
        else -> "unknown"
    }

    private fun num(o: JSONObject, key: String, i: Int): Double? = o.optJSONArray(key)?.let { if (it.isNull(i)) null else it.optDouble(i).takeIf { v -> !v.isNaN() } }

    /** Weather for one place and a date range: the recent past from the forecast API (with UV), older days from the archive. */
    private fun fetchRange(lat: Double, lon: Double, from: String, to: String, today: String): Pair<Map<String, Weather>, Map<String, Map<String, Double?>>> {
        val split = LocalDate.parse(today).minusDays(85).toString()
        val daily = "temperature_2m_max,temperature_2m_min,surface_pressure_mean,uv_index_max,weather_code"
        val ranges = mutableListOf<Triple<String, String, String>>()
        if (from < split) ranges += Triple("https://archive-api.open-meteo.com/v1/archive", from, if (to < split) to else LocalDate.parse(split).minusDays(1).toString())
        if (to >= split) ranges += Triple("https://api.open-meteo.com/v1/forecast", if (from > split) from else split, to)
        val weather = mutableMapOf<String, Weather>()
        for ((base, start, end) in ranges) {
            val json = get("$base?" + q("latitude" to "$lat", "longitude" to "$lon", "daily" to daily, "start_date" to start, "end_date" to end, "timezone" to "auto"))
            val d = json.optJSONObject("daily") ?: continue
            val times = d.optJSONArray("time") ?: continue
            for (i in 0 until times.length()) {
                val max = num(d, "temperature_2m_max", i); val min = num(d, "temperature_2m_min", i)
                weather[times.getString(i)] = Weather(times.getString(i), if (max != null && min != null) (max + min) / 2 else null,
                    num(d, "surface_pressure_mean", i), null, num(d, "weather_code", i)?.toInt()?.let(::condition), emptyMap(), num(d, "uv_index_max", i))
            }
        }
        val pollen = mutableMapOf<String, MutableMap<String, Double?>>()
        try {
            val json = get("https://air-quality-api.open-meteo.com/v1/air-quality?" + q("latitude" to "$lat", "longitude" to "$lon",
                "hourly" to SPECIES.joinToString(",") { "${it}_pollen" }, "start_date" to from, "end_date" to to, "timezone" to "auto"))
            val h = json.optJSONObject("hourly")
            val times = h?.optJSONArray("time")
            if (h != null && times != null) for (i in 0 until times.length()) {
                val day = pollen.getOrPut(times.getString(i).take(10)) { SPECIES.associateWith<String, Double?> { null }.toMutableMap() }
                for (s in SPECIES) num(h, "${s}_pollen", i)?.let { v -> day[s] = maxOf(day[s] ?: 0.0, v) }
            }
        } catch (_: Exception) {
            // Pollen only exists for Europe and recent years; weather alone is fine.
        }
        return weather to pollen
    }

    /**
     * Makes sure each date (not in the future) has weather for its place. Today is refreshed after 3 hours, a
     * past day once more after it is over. Network failures keep what is stored. Does nothing while weather is off.
     */
    fun ensure(diary: Diary, dates: Collection<String>) {
        val profile = diary.profile()
        if (!profile.weatherEnabled) return
        val today = Times.today().toString()
        val wanted = dates.filter { it <= today }.distinct()
        if (wanted.isEmpty()) return
        val home = if (profile.locationLat != null && profile.locationLon != null) profile.locationLat to profile.locationLon else null
        val own = diary.dayPlaces(wanted)
        val stored = diary.weatherRows(wanted)
        val groups = linkedMapOf<String, Pair<Pair<Double, Double>, MutableList<String>>>()
        for (date in wanted) {
            val place = own[date] ?: home ?: continue
            val row = stored[date]
            if (row != null) {
                val (lat, lon, fetchedAt) = row
                // Rows from before places were stored were fetched for home.
                val rowPlace = if (lat != null && lon != null) lat to lon else home
                val fetched = parseFetched(fetchedAt)
                // A past day is final once fetched after it ended; before that it held a forecast for the rest of the day.
                val fresh = if (date < today) !fetched.isBefore(LocalDate.parse(date).plusDays(1).atStartOfDay(Times.zone()).toInstant())
                    else Instant.now().epochSecond - fetched.epochSecond < 3 * 3600
                if (fresh && rowPlace != null && samePlace(rowPlace, place)) continue
            }
            val key = "%.2f,%.2f".format(Locale.ROOT, place.first, place.second)
            groups.getOrPut(key) { place to mutableListOf() }.second += date
        }
        for ((place, group) in groups.values) {
            group.sort()
            val (weather, pollen) = try {
                // One extra day before, for the pressure change of the first day.
                fetchRange(place.first, place.second, LocalDate.parse(group.first()).minusDays(1).toString(), group.last(), today)
            } catch (_: Exception) { continue }
            for (date in group) {
                val w = weather[date] ?: continue
                val before = weather[LocalDate.parse(date).minusDays(1).toString()]?.airPressureHpa
                diary.saveWeather(w.copy(pressureDeltaHpa = if (w.airPressureHpa != null && before != null) w.airPressureHpa - before else null,
                    pollen = pollen[date] ?: emptyMap()), place.first, place.second)
            }
        }
    }

    /** fetched_at is SQLite's "YYYY-MM-DD HH:MM:SS" (UTC) or an ISO time. */
    private fun parseFetched(text: String): Instant = runCatching { Instant.parse(text) }.getOrElse {
        runCatching { LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }.getOrDefault(Instant.EPOCH)
    }

    class Place(val name: String, val lat: Double, val lon: Double)

    /** Place names for a search; the text and the app language go to Open-Meteo, so only with weather on. */
    fun search(diary: Diary, text: String): List<Place> {
        if (!diary.profile().weatherEnabled || text.isBlank()) return emptyList()
        return try {
            val json = get("https://geocoding-api.open-meteo.com/v1/search?" + q("name" to text.trim().take(120), "count" to "5",
                "language" to Locale.getDefault().language.takeIf { it.matches(Regex("[a-z]{2}")) }.orEmpty().ifEmpty { "en" }, "format" to "json"), 5_000)
            val results = json.optJSONArray("results") ?: return emptyList()
            (0 until results.length()).map { results.getJSONObject(it) }.map { r ->
                Place(listOfNotNull(r.optString("name").ifEmpty { null }, r.optString("admin1").ifEmpty { null }, r.optString("country").ifEmpty { null })
                    .joinToString(", "), r.getDouble("latitude"), r.getDouble("longitude"))
            }
        } catch (_: Exception) { emptyList() }
    }
}
