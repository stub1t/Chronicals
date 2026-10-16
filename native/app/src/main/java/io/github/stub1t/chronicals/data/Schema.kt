package io.github.stub1t.chronicals.data

import android.database.sqlite.SQLiteDatabase

/**
 * The tables as the Capacitor app's migrations (the .sql files in migrations/) left them, so the native app opens
 * an existing diary in place and a fresh install gets the same layout. Columns that an older app
 * version did not have yet are added.
 */
object Schema {
    /** PRAGMA user_version once the native app has adopted the file. */
    const val VERSION = 1

    private val TABLES = mapOf(
        "entries" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "date" to "text NOT NULL",
            "note" to "text",
            "created_at" to "text DEFAULT (datetime('now')) NOT NULL",
            "updated_at" to "text DEFAULT (datetime('now')) NOT NULL",
            "wellbeing" to "integer",
            "location_lat" to "real",
            "location_lon" to "real",
            "location_name" to "text",
            "location_source" to "text",
        ),
        "entry_types" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "name" to "text NOT NULL",
            "icon" to "text",
            "color" to "text",
            "metric_schema" to "text NOT NULL",
            "category" to "text DEFAULT 'symptom' NOT NULL",
            "sort_order" to "integer DEFAULT 0 NOT NULL",
            "is_built_in" to "integer DEFAULT false NOT NULL",
            "created_at" to "text DEFAULT (datetime('now')) NOT NULL",
            "hidden" to "integer DEFAULT false NOT NULL",
            "extra_options" to "text",
        ),
        "entry_type_logs" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "entry_id" to "text NOT NULL REFERENCES entries(id) ON UPDATE no action ON DELETE cascade",
            "entry_type_id" to "text NOT NULL REFERENCES entry_types(id) ON UPDATE no action ON DELETE no action",
            "start_time" to "text NOT NULL",
            "end_time" to "text",
            "metric_values" to "text DEFAULT '{}' NOT NULL",
            "intensity_cache" to "real",
            "created_at" to "text DEFAULT (datetime('now')) NOT NULL",
        ),
        "triggers" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "name" to "text NOT NULL",
            "category" to "text NOT NULL",
            "is_built_in" to "integer DEFAULT false NOT NULL",
            "created_at" to "text DEFAULT (datetime('now')) NOT NULL",
            "hidden" to "integer DEFAULT false NOT NULL",
        ),
        "entry_triggers" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "entry_id" to "text NOT NULL REFERENCES entries(id) ON UPDATE no action ON DELETE cascade",
            "trigger_id" to "text NOT NULL REFERENCES triggers(id) ON UPDATE no action ON DELETE cascade",
            "note" to "text",
        ),
        "environmental_data" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "date" to "text NOT NULL",
            "temperature_c" to "real",
            "air_pressure_hpa" to "real",
            "pressure_delta_hpa" to "real",
            "weather_condition" to "text",
            "pollen_grass" to "real",
            "pollen_birch" to "real",
            "pollen_alder" to "real",
            "pollen_mugwort" to "real",
            "uv_index" to "real",
            "fetched_at" to "text DEFAULT (datetime('now')) NOT NULL",
            "pollen_olive" to "real",
            "pollen_ragweed" to "real",
            "lat" to "real",
            "lon" to "real",
        ),
        "user_profile" to listOf(
            "id" to "text PRIMARY KEY DEFAULT 'default' NOT NULL",
            "display_name" to "text",
            "blood_type" to "text",
            "location_lat" to "real",
            "location_lon" to "real",
            "location_name" to "text",
            "updated_at" to "text DEFAULT (datetime('now')) NOT NULL",
            "weather_enabled" to "integer DEFAULT false NOT NULL",
        ),
        "medications" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "name" to "text NOT NULL",
            "dosage" to "text",
            "unit" to "text",
            "start_date" to "text NOT NULL",
            "end_date" to "text",
            "notes" to "text",
            "is_active" to "integer DEFAULT true NOT NULL",
            "created_at" to "text DEFAULT (datetime('now')) NOT NULL",
            "reminder_times" to "text",
            "for_types" to "text",
        ),
        "medication_changes" to listOf(
            "id" to "text PRIMARY KEY NOT NULL",
            "medication_id" to "text REFERENCES medications(id) ON UPDATE no action ON DELETE set null",
            "change_type" to "text NOT NULL",
            "old_dosage" to "text",
            "new_dosage" to "text",
            "change_date" to "text NOT NULL",
            "reason" to "text",
            "notes" to "text",
            "created_at" to "text DEFAULT (datetime('now')) NOT NULL",
        ),
    )

    private val INDEXES = listOf(
        "CREATE INDEX IF NOT EXISTS entries_date_idx ON entries (date)",
        "CREATE UNIQUE INDEX IF NOT EXISTS entry_types_name_unique ON entry_types (name)",
        "CREATE INDEX IF NOT EXISTS env_date_idx ON environmental_data (date)",
        "CREATE UNIQUE INDEX IF NOT EXISTS environmental_data_date_unique ON environmental_data (date)",
        "CREATE INDEX IF NOT EXISTS et_entry_idx ON entry_triggers (entry_id)",
        "CREATE INDEX IF NOT EXISTS et_trigger_idx ON entry_triggers (trigger_id)",
        "CREATE UNIQUE INDEX IF NOT EXISTS et_unique_pair ON entry_triggers (entry_id, trigger_id)",
        "CREATE INDEX IF NOT EXISTS etl_entry_idx ON entry_type_logs (entry_id)",
        "CREATE INDEX IF NOT EXISTS etl_start_idx ON entry_type_logs (start_time)",
        "CREATE INDEX IF NOT EXISTS etl_type_idx ON entry_type_logs (entry_type_id)",
        "CREATE INDEX IF NOT EXISTS mc_date_idx ON medication_changes (change_date)",
        "CREATE INDEX IF NOT EXISTS triggers_category_idx ON triggers (category)",
        "CREATE UNIQUE INDEX IF NOT EXISTS triggers_name_unique ON triggers (name)",
    )

    fun apply(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for ((table, columns) in TABLES) {
                db.execSQL("CREATE TABLE IF NOT EXISTS $table (${columns.joinToString { (name, def) -> "$name $def" }})")
                val existing = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(1)) }
                }
                for ((name, def) in columns) {
                    // ALTER TABLE cannot add a column with REFERENCES and a default together; none of the added ones has both.
                    if (name !in existing) db.execSQL("ALTER TABLE $table ADD COLUMN $name ${def.replace(" PRIMARY KEY", "")}")
                }
            }
            INDEXES.forEach(db::execSQL)
            db.version = VERSION
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
