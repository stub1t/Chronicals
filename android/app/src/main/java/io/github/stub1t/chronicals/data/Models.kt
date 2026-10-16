package io.github.stub1t.chronicals.data

/** One form field of an entry type, from its `metric_schema` JSON. */
data class Field(
    val key: String,
    val label: String,
    /** slider, select, checklist, textarea, text or number */
    val type: String,
    val required: Boolean,
    val min: Int = 1,
    val max: Int = 5,
    val step: Int = 1,
    val options: List<String> = emptyList(),
    val placeholder: String? = null,
    /** The user may add own options (stored in the type's extra options). */
    val extendable: Boolean = false,
    val units: List<String> = emptyList(),
)

data class EntryType(
    val id: String,
    val name: String,
    /** Emoji, 'lucide:<key>' or a letter for built-ins. */
    val icon: String?,
    val color: String?,
    val fields: List<Field>,
    /** symptom, event, note or meal */
    val category: String,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
    val hidden: Boolean,
    val extraOptions: Map<String, List<String>>,
) {
    /** Options of a field, the built-in ones first, then the user's own. */
    fun optionsFor(field: Field): List<String> = field.options + (extraOptions[field.key] ?: emptyList()).filter { it !in field.options }
}

/** A value in a log's `metric_values`: numbers, text or lists of text. */
typealias MetricValues = Map<String, Any?>

data class Log(
    val id: String,
    val entryId: String,
    val entryTypeId: String,
    /** UTC ISO time as JavaScript writes it, e.g. 2026-10-14T06:40:00.000Z */
    val startTime: String,
    val endTime: String?,
    val values: MetricValues,
    val intensity: Double?,
)

data class Trigger(
    val id: String,
    val name: String,
    /** food, environment, activity, medication or custom */
    val category: String,
    val isBuiltIn: Boolean,
    val hidden: Boolean,
)

data class DayTrigger(val id: String, val triggerId: String, val name: String, val category: String, val note: String?, val isBuiltIn: Boolean)

data class Entry(
    val id: String,
    /** YYYY-MM-DD */
    val date: String,
    val note: String?,
    val wellbeing: Int?,
    val locationLat: Double?,
    val locationLon: Double?,
    val locationName: String?,
    val locationSource: String?,
    val logs: List<Log>,
    val triggers: List<DayTrigger>,
)

data class Profile(
    val displayName: String?,
    val bloodType: String?,
    val locationLat: Double?,
    val locationLon: Double?,
    val locationName: String?,
    val weatherEnabled: Boolean,
)

data class Medication(
    val id: String,
    val name: String,
    val dosage: String?,
    val unit: String?,
    val startDate: String,
    val endDate: String?,
    val notes: String?,
    val isActive: Boolean,
    val reminderTimes: List<String>,
    val forTypes: List<String>,
)

data class MedicationChange(
    val id: String,
    val medicationId: String?,
    /** started, dosage_change or stopped */
    val changeType: String,
    val oldDosage: String?,
    val newDosage: String?,
    val changeDate: String,
    val reason: String?,
    val notes: String?,
)

data class Weather(
    val date: String,
    val temperatureC: Double?,
    val airPressureHpa: Double?,
    val pressureDeltaHpa: Double?,
    val condition: String?,
    val pollen: Map<String, Double?>,
    val uvIndex: Double?,
)
