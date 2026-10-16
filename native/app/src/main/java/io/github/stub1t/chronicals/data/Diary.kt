package io.github.stub1t.chronicals.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * The diary on the phone: the same SQLite file the Capacitor app wrote (files/chronicals.db), opened in
 * place. Reads are plain functions meant for a background thread; every write bumps [changes], which
 * the screens watch to reload.
 */
class Diary(context: Context) {
    val file = File(context.filesDir, FILE_NAME)
    private val cache = context.cacheDir
    @Volatile private var db: SQLiteDatabase = open(file)

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private fun changed() { _changes.value++ }

    // ─── Entry types ──────────────────────────────────────────────────────────

    fun entryTypes(): List<EntryType> =
        db.rawQuery("SELECT id, name, icon, color, metric_schema, category, sort_order, is_built_in, hidden, extra_options FROM entry_types ORDER BY sort_order", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.toEntryType()) } }

    fun setTypeHidden(id: String, hidden: Boolean) = write {
        db.update("entry_types", ContentValues().apply { put("hidden", hidden.int) }, "id = ?", arrayOf(id))
    }

    /** Adds an own option to a field of a type (built-in or own); it sticks across seeds. */
    fun addOption(type: EntryType, fieldKey: String, option: String) = write {
        val extra = type.extraOptions.toMutableMap()
        extra[fieldKey] = (extra[fieldKey] ?: emptyList()) + option
        db.update("entry_types", ContentValues().apply { put("extra_options", Json.stringify(extra)) }, "id = ?", arrayOf(type.id))
    }

    fun setExtraOptions(id: String, extra: Map<String, List<String>>) = write {
        db.update("entry_types", ContentValues().apply { put("extra_options", Json.stringify(extra)) }, "id = ?", arrayOf(id))
    }

    // ─── Triggers ─────────────────────────────────────────────────────────────

    /** All triggers, deleted ones included (marked hidden), so past days can still name them. */
    fun triggers(): List<Trigger> =
        db.rawQuery("SELECT id, name, category, is_built_in, hidden FROM triggers ORDER BY category, name", null).use { c ->
            buildList { while (c.moveToNext()) add(Trigger(c.getString(0), c.getString(1), c.getString(2), c.getInt(3) != 0, c.getInt(4) != 0)) }
        }

    /** A new trigger, or a deleted one with the same name brought back; null when a visible one has the name. */
    fun createTrigger(name: String, category: String = "custom"): Trigger? {
        val same = triggers().firstOrNull { it.name == name }
        if (same != null && !same.hidden) return null
        write {
            if (same != null) db.update("triggers", ContentValues().apply { put("hidden", 0); put("category", category) }, "id = ?", arrayOf(same.id))
            else db.insert("triggers", null, ContentValues().apply {
                put("id", uuid()); put("name", name); put("category", category); put("is_built_in", 0)
            })
        }
        return triggers().first { it.name == name }
    }

    // ─── Entries (one per day) ────────────────────────────────────────────────

    fun entryByDate(date: String): Entry? = entriesBetween(date, date).firstOrNull()

    /** Full entries for every day from [from] to [to] (inclusive, YYYY-MM-DD), oldest first. */
    fun entriesBetween(from: String, to: String): List<Entry> {
        val logs = db.rawQuery(
            """SELECT l.id, l.entry_id, l.entry_type_id, l.start_time, l.end_time, l.metric_values, l.intensity_cache
               FROM entry_type_logs l JOIN entries e ON e.id = l.entry_id WHERE e.date BETWEEN ? AND ? ORDER BY l.start_time""",
            arrayOf(from, to)
        ).use { c -> buildList { while (c.moveToNext()) add(c.toLog()) } }.groupBy { it.entryId }
        val dayTriggers = db.rawQuery(
            """SELECT et.id, et.entry_id, et.trigger_id, t.name, t.category, et.note, t.is_built_in
               FROM entry_triggers et JOIN entries e ON e.id = et.entry_id LEFT JOIN triggers t ON t.id = et.trigger_id
               WHERE e.date BETWEEN ? AND ?""",
            arrayOf(from, to)
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1) to DayTrigger(c.getString(0), c.getString(2), c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5), c.getInt(6) != 0)) }
        }.groupBy({ it.first }, { it.second })
        return db.rawQuery(
            "SELECT id, date, note, wellbeing, location_lat, location_lon, location_name, location_source FROM entries WHERE date BETWEEN ? AND ? ORDER BY date",
            arrayOf(from, to)
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    add(Entry(id, c.getString(1), c.getStringOrNull(2), c.getIntOrNull(3), c.getDoubleOrNull(4), c.getDoubleOrNull(5),
                        c.getStringOrNull(6), c.getStringOrNull(7), logs[id] ?: emptyList(), dayTriggers[id] ?: emptyList()))
                }
            }
        }
    }

    fun countEntries(): Int = db.rawQuery("SELECT count(*) FROM entries", null).use { it.moveToFirst(); it.getInt(0) }

    /** The entry id for a date, created if missing. */
    fun ensureEntry(date: String): String {
        db.rawQuery("SELECT id FROM entries WHERE date = ?", arrayOf(date)).use { if (it.moveToFirst()) return it.getString(0) }
        val id = uuid()
        write { db.insert("entries", null, ContentValues().apply { put("id", id); put("date", date) }) }
        return id
    }

    fun setNote(date: String, note: String?) {
        val id = ensureEntry(date)
        write {
            db.execSQL("UPDATE entries SET note = ?, updated_at = datetime('now') WHERE id = ?", arrayOf(note?.ifBlank { null }, id))
        }
    }

    fun addDayTrigger(date: String, triggerId: String) {
        val entryId = ensureEntry(date)
        write {
            db.insertWithOnConflict("entry_triggers", null, ContentValues().apply {
                put("id", uuid()); put("entry_id", entryId); put("trigger_id", triggerId)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun removeDayTrigger(date: String, triggerId: String) = write {
        db.execSQL("DELETE FROM entry_triggers WHERE trigger_id = ? AND entry_id = (SELECT id FROM entries WHERE date = ?)", arrayOf(triggerId, date))
    }

    // ─── Logs ─────────────────────────────────────────────────────────────────

    fun addLog(date: String, typeId: String, startTime: String, endTime: String?, values: MetricValues) {
        val entryId = ensureEntry(date)
        write {
            db.insert("entry_type_logs", null, ContentValues().apply {
                put("id", uuid()); put("entry_id", entryId); put("entry_type_id", typeId); put("start_time", startTime)
                if (endTime == null) putNull("end_time") else put("end_time", endTime)
                put("metric_values", Json.stringify(values))
                intensityOf(values)?.let { put("intensity_cache", it) } ?: putNull("intensity_cache")
            })
        }
    }

    fun updateLog(id: String, startTime: String, endTime: String?, values: MetricValues) = write {
        db.update("entry_type_logs", ContentValues().apply {
            put("start_time", startTime)
            if (endTime == null) putNull("end_time") else put("end_time", endTime)
            put("metric_values", Json.stringify(values))
            intensityOf(values)?.let { put("intensity_cache", it) } ?: putNull("intensity_cache")
        }, "id = ?", arrayOf(id))
    }

    fun endLog(id: String, endTime: String) = write {
        db.update("entry_type_logs", ContentValues().apply { put("end_time", endTime) }, "id = ?", arrayOf(id))
    }

    fun deleteLog(id: String) = write { db.delete("entry_type_logs", "id = ?", arrayOf(id)) }

    // ─── Profile ──────────────────────────────────────────────────────────────

    fun profile(): Profile =
        db.rawQuery("SELECT display_name, blood_type, location_lat, location_lon, location_name, weather_enabled FROM user_profile WHERE id = 'default'", null).use { c ->
            if (!c.moveToFirst()) Profile(null, null, null, null, null, false)
            else Profile(c.getStringOrNull(0), c.getStringOrNull(1), c.getDoubleOrNull(2), c.getDoubleOrNull(3), c.getStringOrNull(4), c.getInt(5) != 0)
        }

    fun updateProfile(displayName: String?, bloodType: String?) = write {
        db.execSQL("UPDATE user_profile SET display_name = ?, blood_type = ?, updated_at = datetime('now') WHERE id = 'default'",
            arrayOf(displayName?.trim()?.ifEmpty { null }, bloodType))
    }

    /** The home place, for the weather; null clears it. */
    fun setHome(lat: Double?, lon: Double?, name: String?) = write {
        db.execSQL("UPDATE user_profile SET location_lat = ?, location_lon = ?, location_name = ?, updated_at = datetime('now') WHERE id = 'default'",
            arrayOf<Any?>(lat, lon, name))
    }

    /** Nothing goes to Open-Meteo until this is on. */
    fun setWeatherEnabled(on: Boolean) = write {
        db.execSQL("UPDATE user_profile SET weather_enabled = ?, updated_at = datetime('now') WHERE id = 'default'", arrayOf(on.int))
    }

    // ─── Entry types (own ones) ───────────────────────────────────────────────

    /** A new own type; null when the name is taken. */
    fun createEntryType(name: String, icon: String?, color: String?, category: String, metricSchema: String): String? {
        if (exists("SELECT 1 FROM entry_types WHERE name = ?", name)) return null
        val id = uuid()
        val order = db.rawQuery("SELECT coalesce(max(sort_order), -1) + 1 FROM entry_types", null).use { it.moveToFirst(); it.getInt(0) }
        write {
            db.insert("entry_types", null, ContentValues().apply {
                put("id", id); put("name", name); put("icon", icon); put("color", color ?: "#94a3b8"); put("metric_schema", metricSchema)
                put("category", category); put("sort_order", order); put("is_built_in", 0)
            })
        }
        return id
    }

    /** Built-in types are refreshed by the seed, so only own types change here; false when the name is taken. */
    fun updateEntryType(id: String, name: String, icon: String?, color: String?, metricSchema: String): Boolean {
        if (exists("SELECT 1 FROM entry_types WHERE name = ? AND id != ?", name, id)) return false
        write {
            db.update("entry_types", ContentValues().apply { put("name", name); put("icon", icon); put("color", color); put("metric_schema", metricSchema) },
                "id = ? AND is_built_in = 0", arrayOf(id))
        }
        return true
    }

    /** Deletes an own type without logs; a type in use can only be hidden. False when it is in use. */
    fun deleteEntryType(id: String): Boolean {
        if (exists("SELECT 1 FROM entry_type_logs WHERE entry_type_id = ? LIMIT 1", id)) return false
        write { db.delete("entry_types", "id = ? AND is_built_in = 0", arrayOf(id)) }
        return true
    }

    // ─── Triggers (Me) ────────────────────────────────────────────────────────

    /** Rename or regroup; a renamed built-in becomes the user's own, so it is no longer translated. False when the name is taken. */
    fun updateTrigger(id: String, name: String, category: String): Boolean {
        val current = triggers().firstOrNull { it.id == id } ?: return false
        val renamed = name != current.name
        if (renamed && exists("SELECT 1 FROM triggers WHERE name = ?", name)) return false
        write {
            db.update("triggers", ContentValues().apply {
                if (renamed) { put("name", name); put("is_built_in", 0) }
                put("category", category)
            }, "id = ?", arrayOf(id))
        }
        return true
    }

    fun restoreTrigger(id: String) = write { db.update("triggers", ContentValues().apply { put("hidden", 0) }, "id = ?", arrayOf(id)) }

    /** Built-in triggers and triggers used on any day are only hidden, so history keeps them; unused own ones are removed. */
    fun deleteTrigger(id: String) {
        val trigger = triggers().firstOrNull { it.id == id } ?: return
        val used = exists("SELECT 1 FROM entry_triggers WHERE trigger_id = ? LIMIT 1", id)
        write {
            if (trigger.isBuiltIn || used) db.update("triggers", ContentValues().apply { put("hidden", 1) }, "id = ?", arrayOf(id))
            else db.delete("triggers", "id = ?", arrayOf(id))
        }
    }

    // ─── Medications ──────────────────────────────────────────────────────────

    fun createMedication(name: String, dosage: String?, unit: String?, startDate: String, reminderTimes: List<String>, forTypes: List<String>) {
        write {
            db.insert("medications", null, ContentValues().apply {
                put("id", uuid()); put("name", name); put("dosage", dosage); put("unit", unit); put("start_date", startDate); put("is_active", 1)
                put("reminder_times", reminderTimes.takeIf { it.isNotEmpty() }?.let(Json::stringify))
                put("for_types", forTypes.takeIf { it.isNotEmpty() }?.let(Json::stringify))
            })
        }
        markMedicationDay(startDate, listOfNotNull(name, dosage?.let { d -> if (unit.isNullOrEmpty()) d else "$d $unit" }).joinToString(" "))
    }

    fun updateMedication(id: String, name: String, dosage: String?, unit: String?, startDate: String, reminderTimes: List<String>, forTypes: List<String>) = write {
        db.update("medications", ContentValues().apply {
            put("name", name); put("dosage", dosage); put("unit", unit); put("start_date", startDate)
            put("reminder_times", reminderTimes.takeIf { it.isNotEmpty() }?.let(Json::stringify))
            put("for_types", forTypes.takeIf { it.isNotEmpty() }?.let(Json::stringify))
        }, "id = ?", arrayOf(id))
    }

    fun setDosage(id: String, dosage: String) = write { db.update("medications", ContentValues().apply { put("dosage", dosage) }, "id = ?", arrayOf(id)) }

    fun deleteMedication(id: String) = write { db.delete("medications", "id = ?", arrayOf(id)) }

    /** A start, stop or dose change; a start or stop also moves the medication between the active and past lists. */
    fun addMedicationChange(medicationId: String, changeType: String, changeDate: String, oldDosage: String? = null, newDosage: String? = null, reason: String? = null) {
        write {
            db.beginTransaction()
            try {
                db.insert("medication_changes", null, ContentValues().apply {
                    put("id", uuid()); put("medication_id", medicationId); put("change_type", changeType); put("old_dosage", oldDosage)
                    put("new_dosage", newDosage); put("change_date", changeDate); put("reason", reason)
                })
                if (changeType != "dosage_change") {
                    val stopped = changeType == "stopped"
                    db.update("medications", ContentValues().apply { put("is_active", (!stopped).int); put("end_date", if (stopped) changeDate else null) },
                        "id = ?", arrayOf(medicationId))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        val med = medications().firstOrNull { it.id == medicationId } ?: return
        val dose = if (changeType == "dosage_change") ": " + listOfNotNull(oldDosage, newDosage).filter { it.isNotEmpty() }.joinToString(" → ") else ""
        markMedicationDay(changeDate, med.name + dose)
    }

    /** A start, stop or dose change is also the trigger "Medication change" on that day, unless the user deleted that trigger. */
    private fun markMedicationDay(date: String, note: String) {
        val trigger = triggers().firstOrNull { it.id == "trig-medication-change" }
        if (trigger == null || trigger.hidden) return
        val entryId = ensureEntry(date)
        val linked = db.rawQuery("SELECT id, note FROM entry_triggers WHERE entry_id = ? AND trigger_id = ?", arrayOf(entryId, trigger.id))
            .use { if (it.moveToFirst()) it.getString(0) to it.getStringOrNull(1) else null }
        write {
            if (linked == null) db.insert("entry_triggers", null, ContentValues().apply {
                put("id", uuid()); put("entry_id", entryId); put("trigger_id", trigger.id); put("note", note)
            })
            else if (linked.second?.contains(note) != true) db.update("entry_triggers", ContentValues().apply {
                put("note", listOfNotNull(linked.second, note).filter { it.isNotEmpty() }.joinToString("; "))
            }, "id = ?", arrayOf(linked.first))
        }
    }


    fun medications(): List<Medication> =
        db.rawQuery("SELECT id, name, dosage, unit, start_date, end_date, notes, is_active, reminder_times, for_types FROM medications ORDER BY is_active DESC, name", null).use { c ->
            buildList {
                while (c.moveToNext()) add(Medication(c.getString(0), c.getString(1), c.getStringOrNull(2), c.getStringOrNull(3), c.getString(4),
                    c.getStringOrNull(5), c.getStringOrNull(6), c.getInt(7) != 0, Json.parseStringList(c.getStringOrNull(8)), Json.parseStringList(c.getStringOrNull(9))))
            }
        }

    fun medicationChanges(): List<MedicationChange> =
        db.rawQuery("SELECT id, medication_id, change_type, old_dosage, new_dosage, change_date, reason, notes FROM medication_changes ORDER BY change_date DESC", null).use { c ->
            buildList {
                while (c.moveToNext()) add(MedicationChange(c.getString(0), c.getStringOrNull(1), c.getString(2), c.getStringOrNull(3), c.getStringOrNull(4),
                    c.getString(5), c.getStringOrNull(6), c.getStringOrNull(7)))
            }
        }

    // ─── Weather ──────────────────────────────────────────────────────────────

    fun weatherBetween(from: String, to: String): List<Weather> =
        db.rawQuery(
            """SELECT date, temperature_c, air_pressure_hpa, pressure_delta_hpa, weather_condition, uv_index,
               pollen_alder, pollen_birch, pollen_grass, pollen_mugwort, pollen_olive, pollen_ragweed
               FROM environmental_data WHERE date BETWEEN ? AND ? ORDER BY date""", arrayOf(from, to)
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val pollen = listOf("alder", "birch", "grass", "mugwort", "olive", "ragweed").mapIndexed { i, s -> s to c.getDoubleOrNull(6 + i) }.toMap()
                    add(Weather(c.getString(0), c.getDoubleOrNull(1), c.getDoubleOrNull(2), c.getDoubleOrNull(3), c.getStringOrNull(4), pollen, c.getDoubleOrNull(5)))
                }
            }
        }

    /** Stored rows with what decides whether to fetch again: date to (lat, lon, fetched_at). */
    fun weatherRows(dates: Collection<String>): Map<String, Triple<Double?, Double?, String>> {
        if (dates.isEmpty()) return emptyMap()
        val marks = dates.joinToString(",") { "?" }
        return db.rawQuery("SELECT date, lat, lon, fetched_at FROM environmental_data WHERE date IN ($marks)", dates.toTypedArray()).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), Triple(c.getDoubleOrNull(1), c.getDoubleOrNull(2), c.getString(3))) }
        }
    }

    /** The day's own place (set while travelling), by date. */
    fun dayPlaces(dates: Collection<String>): Map<String, Pair<Double, Double>> {
        if (dates.isEmpty()) return emptyMap()
        val marks = dates.joinToString(",") { "?" }
        return db.rawQuery("SELECT date, location_lat, location_lon FROM entries WHERE location_lat IS NOT NULL AND location_lon IS NOT NULL AND date IN ($marks)",
            dates.toTypedArray()).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getDouble(1) to c.getDouble(2)) } }
    }

    fun saveWeather(w: Weather, lat: Double, lon: Double) = write {
        val values = ContentValues().apply {
            put("temperature_c", w.temperatureC); put("air_pressure_hpa", w.airPressureHpa); put("pressure_delta_hpa", w.pressureDeltaHpa)
            put("weather_condition", w.condition); put("uv_index", w.uvIndex)
            for (s in listOf("alder", "birch", "grass", "mugwort", "olive", "ragweed")) put("pollen_$s", w.pollen[s])
            put("lat", lat); put("lon", lon); put("fetched_at", Times.isoNow())
        }
        if (db.update("environmental_data", values, "date = ?", arrayOf(w.date)) == 0) {
            db.insert("environmental_data", null, values.apply { put("id", uuid()); put("date", w.date) })
        }
    }

    /** Where a day was spent when away; null goes back to home. source: auto or manual. */
    fun setDayPlace(date: String, lat: Double?, lon: Double?, name: String?, source: String?) {
        val id = ensureEntry(date)
        write {
            db.execSQL("UPDATE entries SET location_lat = ?, location_lon = ?, location_name = ?, location_source = ?, updated_at = datetime('now') WHERE id = ?",
                arrayOf<Any?>(lat, lon, name, source, id))
        }
    }

    // ─── Backup ───────────────────────────────────────────────────────────────

    /** The whole diary as one SQLite file; the write lock keeps a write from landing halfway through the copy. */
    fun exportTo(out: java.io.OutputStream) {
        db.beginTransaction()
        try {
            file.inputStream().use { it.copyTo(out) }
        } finally {
            db.endTransaction()
        }
    }

    class BackupError(message: String) : Exception(message)

    /**
     * Replaces the diary with a backup (from this app or the Capacitor app) after checking it, and returns
     * how many days it has. Port of the checks in src/lib/data/backup.ts.
     */
    fun restoreFrom(input: java.io.InputStream): Int {
        val candidate = File(cache, "restore.db")
        candidate.delete()
        input.use { src -> candidate.outputStream().use { src.copyTo(it, 64 * 1024) } }
        if (candidate.length() > 50L * 1024 * 1024) throw BackupError("Backup file is larger than 50 MB")
        val header = candidate.inputStream().use { it.readNBytesCompat(16) }
        if (String(header, Charsets.ISO_8859_1) != "SQLite format 3\u0000") throw BackupError("This file is not an SQLite database")
        // Files saved in WAL mode say so in header bytes 18 and 19; turned back into a plain database.
        java.io.RandomAccessFile(candidate, "rw").use { f ->
            f.seek(18); val a = f.read(); val b = f.read()
            if (a == 2 && b == 2) { f.seek(18); f.write(1); f.write(1) }
        }
        val days = try {
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READWRITE).use { c ->
                val tables = c.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { q -> buildSet { while (q.moveToNext()) add(q.getString(0)) } }
                if (!tables.containsAll(listOf("entries", "entry_types"))) throw BackupError("This file is not a Chronicals backup")
                // Our schema has none; triggers or views could run code on later writes.
                if (c.rawQuery("SELECT count(*) FROM sqlite_master WHERE type IN ('trigger', 'view')", null).use { q -> q.moveToFirst(); q.getInt(0) } > 0)
                    throw BackupError("Backup contains triggers or views")
                if (c.rawQuery("PRAGMA integrity_check", null).use { q -> q.moveToFirst(); q.getString(0) } != "ok") throw BackupError("Backup file is damaged")
                if (c.rawQuery("PRAGMA foreign_key_check", null).use { q -> q.moveToFirst() }) throw BackupError("Backup file has broken references between records")
                if (c.version > Schema.VERSION) throw BackupError("This backup comes from a newer version of Chronicals. Update the app first.")
                c.rawQuery("SELECT count(*) FROM entries", null).use { q -> q.moveToFirst(); q.getInt(0) }
            }
        } catch (e: BackupError) {
            candidate.delete(); throw e
        } catch (e: Exception) {
            candidate.delete(); throw BackupError("This file is not a readable SQLite database")
        }
        synchronized(this) {
            db.close()
            File(file.path + "-journal").delete()
            candidate.copyTo(file, overwrite = true)
            candidate.delete()
            db = open(file)
        }
        changed()
        return days
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun exists(sql: String, vararg args: String): Boolean = db.rawQuery(sql, args).use { it.moveToFirst() }

    private inline fun write(block: () -> Unit) {
        block()
        changed()
    }

    companion object {
        const val FILE_NAME = "chronicals.db"

        fun uuid(): String = UUID.randomUUID().toString()

        fun open(file: File): SQLiteDatabase {
            val db = SQLiteDatabase.openOrCreateDatabase(file, null)
            db.setForeignKeyConstraintsEnabled(true)
            Schema.apply(db)
            Seed.run(db)
            return db
        }

        /** Intensity 1 to 5 for fast queries: the 'intensity' value, or an old 'severity'. */
        fun intensityOf(values: MetricValues): Double? = when (val v = values["intensity"] ?: values["severity"]) {
            is Number -> v.toDouble()
            "mild" -> 2.0
            "moderate" -> 3.0
            "severe" -> 4.0
            else -> null
        }
    }
}

private val Boolean.int get() = if (this) 1 else 0
private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
    val buffer = ByteArray(n)
    var read = 0
    while (read < n) { val r = read(buffer, read, n - read); if (r < 0) break; read += r }
    return buffer.copyOf(read)
}
private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
private fun Cursor.getIntOrNull(i: Int): Int? = if (isNull(i)) null else getInt(i)
private fun Cursor.getDoubleOrNull(i: Int): Double? = if (isNull(i)) null else getDouble(i)

private fun Cursor.toLog() = Log(
    id = getString(0), entryId = getString(1), entryTypeId = getString(2), startTime = getString(3),
    endTime = getStringOrNull(4), values = Json.parseObject(getString(5)), intensity = getDoubleOrNull(6),
)

private fun Cursor.toEntryType(): EntryType {
    val schema = Json.parseObject(getString(4))
    @Suppress("UNCHECKED_CAST")
    val fields = (schema["fields"] as? List<Map<String, Any?>> ?: emptyList()).map { f ->
        val config = f["config"] as? Map<String, Any?> ?: emptyMap()
        Field(
            key = f["key"].toString(),
            label = f["label"]?.toString() ?: "",
            type = f["type"]?.toString() ?: "text",
            required = f["required"] == true,
            min = (config["min"] as? Number)?.toInt() ?: 1,
            max = (config["max"] as? Number)?.toInt() ?: 5,
            step = (config["step"] as? Number)?.toInt() ?: 1,
            options = (config["options"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            placeholder = config["placeholder"]?.toString(),
            extendable = config["extendable"] == true,
            units = (config["units"] as? List<*>)?.map { it.toString() } ?: emptyList(),
        )
    }
    @Suppress("UNCHECKED_CAST")
    val extra = (Json.parseObject(getStringOrNull(9)) as Map<String, Any?>).mapValues { (_, v) -> (v as? List<*>)?.map { it.toString() } ?: emptyList() }
    return EntryType(getString(0), getString(1), getStringOrNull(2), getStringOrNull(3), fields, getString(5), getInt(6), getInt(7) != 0, getInt(8) != 0, extra)
}
