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
    private var db: SQLiteDatabase = open(file)

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

    // ─── Medications ──────────────────────────────────────────────────────────

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

    // ─── Helpers ──────────────────────────────────────────────────────────────

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
