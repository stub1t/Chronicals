package io.github.stub1t.chronicals.data

import org.json.JSONArray
import org.json.JSONObject

/** org.json to plain Kotlin values and back, for the JSON columns. */
object Json {
    fun parseObject(text: String?): Map<String, Any?> =
        if (text.isNullOrBlank()) emptyMap() else runCatching { toMap(JSONObject(text)) }.getOrDefault(emptyMap())

    fun parseStringList(text: String?): List<String> =
        if (text.isNullOrBlank()) emptyList()
        else runCatching { JSONArray(text).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    fun toMap(obj: JSONObject): Map<String, Any?> = obj.keys().asSequence().associateWith { unwrap(obj.opt(it)) }

    private fun unwrap(value: Any?): Any? = when (value) {
        JSONObject.NULL, null -> null
        is JSONObject -> toMap(value)
        is JSONArray -> (0 until value.length()).map { unwrap(value.opt(it)) }
        else -> value
    }

    fun stringify(value: Any?): String = wrap(value).toString()

    private fun wrap(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> value.forEach { (k, v) -> o.put(k.toString(), wrap(v)) } }
        is List<*> -> JSONArray().also { a -> value.forEach { a.put(wrap(it)) } }
        is Float -> value.toDouble()
        else -> value
    }
}
