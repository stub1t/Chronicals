package io.github.stub1t.chronicals.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/** Built-in types, triggers and the profile row, plus upgrades of older diaries. Port of src/lib/data/seed.ts. */
object Seed {
    fun run(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for (type in BUILT_IN_TYPES) {
                if (!exists(db, "entry_types", "id = ?", type.id)) {
                    db.insert("entry_types", null, ContentValues().apply {
                        put("id", type.id); put("name", type.name); put("icon", type.icon); put("color", type.color)
                        put("category", type.category); put("sort_order", type.sortOrder); put("metric_schema", type.metricSchema)
                        put("is_built_in", 1)
                    })
                } else {
                    // Refresh presentation and schema; a renamed built-in keeps its old name if a custom type has the new one.
                    val nameTaken = exists(db, "entry_types", "name = ? AND id != ?", type.name, type.id)
                    db.update("entry_types", ContentValues().apply {
                        if (!nameTaken) put("name", type.name)
                        put("icon", type.icon); put("color", type.color); put("category", type.category)
                        put("sort_order", type.sortOrder); put("metric_schema", type.metricSchema)
                    }, "id = ?", arrayOf(type.id))
                }
            }

            upgradeSeverity(db)
            addBloodSugarDirection(db)
            periodFlowToScale(db)

            // Merge accidental custom period duplicates into the built-in type so old logs survive.
            db.rawQuery("SELECT id FROM entry_types WHERE lower(name) = 'period' AND is_built_in = 0", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    db.execSQL("UPDATE entry_type_logs SET entry_type_id = 'builtin-period' WHERE entry_type_id = ?", arrayOf(id))
                    db.delete("entry_types", "id = ?", arrayOf(id))
                }
            }

            for ((id, name, category) in BUILT_IN_TRIGGERS) {
                if (!exists(db, "triggers", "id = ?", id)) {
                    db.insert("triggers", null, ContentValues().apply {
                        put("id", id); put("name", name); put("category", category); put("is_built_in", 1)
                    })
                }
            }
            // An early pollen trigger, replaced by measured pollen in the weather data.
            db.delete("triggers", "id = ?", arrayOf("trig-pollen"))

            medicationChangeLogsToTriggers(db)

            if (!exists(db, "user_profile", "id = ?", "default")) {
                db.insert("user_profile", null, ContentValues().apply { put("id", "default") })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun exists(db: SQLiteDatabase, table: String, where: String, vararg args: String): Boolean =
        db.rawQuery("SELECT 1 FROM $table WHERE $where LIMIT 1", args).use { it.moveToFirst() }

    private fun logs(db: SQLiteDatabase, where: String, vararg args: String): List<Pair<String, Map<String, Any?>>> =
        db.rawQuery("SELECT id, metric_values FROM entry_type_logs WHERE $where", args).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to Json.parseObject(c.getString(1))) }
        }

    private fun setValues(db: SQLiteDatabase, id: String, values: Map<String, Any?>, intensity: Double? = null, withIntensity: Boolean = false) {
        db.update("entry_type_logs", ContentValues().apply {
            put("metric_values", Json.stringify(values))
            if (withIntensity) if (intensity == null) putNull("intensity_cache") else put("intensity_cache", intensity)
        }, "id = ?", arrayOf(id))
    }

    private val SEVERITY_WORDS = mapOf("mild" to 2, "moderate" to 3, "severe" to 4)

    /** Older 'severity' values become 'intensity' 1 to 5. */
    private fun upgradeSeverity(db: SQLiteDatabase) {
        for ((id, values) in logs(db, "metric_values LIKE '%\"severity\"%'")) {
            if ("severity" !in values) continue
            val severity = values["severity"]
            val rest = values - "severity"
            val intensity: Number? = when (severity) {
                is Number -> severity
                is String -> SEVERITY_WORDS[severity]
                else -> null
            }
            val next = if (intensity == null || "intensity" in rest) rest else rest + ("intensity" to intensity)
            setValues(db, id, next, (next["intensity"] as? Number)?.toDouble(), withIntensity = true)
        }
    }

    /** Low blood sugar became Blood sugar with a direction; older logs were all "too low". */
    private fun addBloodSugarDirection(db: SQLiteDatabase) {
        for ((id, values) in logs(db, "entry_type_id = 'builtin-hypo'")) {
            if ("direction" in values) continue
            setValues(db, id, linkedMapOf<String, Any?>("direction" to "too low") + values)
        }
    }

    private val FLOW_WORDS = mapOf("light" to 2, "medium" to 3, "heavy" to 4)

    /** A period's flow was light, medium or heavy; it is a 1 to 5 scale now. */
    private fun periodFlowToScale(db: SQLiteDatabase) {
        for ((id, values) in logs(db, "entry_type_id = 'builtin-period' AND metric_values LIKE '%\"flow\":\"%'")) {
            val flow = values["flow"] as? String ?: continue
            val rest = values - "flow"
            val level = FLOW_WORDS[flow]
            setValues(db, id, if (level != null) rest + ("flow" to level) else rest)
        }
    }

    /** Medication change used to be an entry type; its logs become the trigger 'Medication change' on their day. */
    private fun medicationChangeLogsToTriggers(db: SQLiteDatabase) {
        val old = "builtin-medication-change"
        db.rawQuery("SELECT id, entry_id, metric_values FROM entry_type_logs WHERE entry_type_id = ?", arrayOf(old)).use { c ->
            while (c.moveToNext()) {
                val logId = c.getString(0)
                val entryId = c.getString(1)
                val v = Json.parseObject(c.getString(2)).mapValues { it.value?.toString() }
                val dose = listOfNotNull(v["old_dosage"], v["new_dosage"]).filter { it.isNotEmpty() }.joinToString(" → ")
                val note = listOfNotNull(v["medication_name"], v["change_type"], dose, v["reason"], v["notes"])
                    .filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null }
                if (!exists(db, "entry_triggers", "entry_id = ? AND trigger_id = 'trig-medication-change'", entryId)) {
                    db.insert("entry_triggers", null, ContentValues().apply {
                        put("id", "medchange-$logId"); put("entry_id", entryId); put("trigger_id", "trig-medication-change")
                        if (note == null) putNull("note") else put("note", note)
                    })
                }
                db.delete("entry_type_logs", "id = ?", arrayOf(logId))
            }
        }
        db.delete("entry_types", "id = ?", arrayOf(old))
    }
}
