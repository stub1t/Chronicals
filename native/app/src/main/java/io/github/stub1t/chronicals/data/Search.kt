package io.github.stub1t.chronicals.data

import java.text.Normalizer

/** Day search. Port of src/lib/search.ts. */
object Search {
    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Lower case without accents, so "Schlafe" finds "Schläfe" and "cafe" finds "Café". */
    fun normalize(text: String): String = DIACRITICS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

    /** Note, type names, triggers and form values; built-in text in its stored and translated form. */
    fun text(entry: Entry, typeNames: Map<String, String>, translate: (String) -> String): String {
        val parts = mutableListOf<String>()
        fun add(value: Any?) {
            when (value) {
                is String -> if (value.isNotEmpty()) { parts += value; parts += translate(value) }
                is List<*> -> value.forEach(::add)
            }
        }
        add(entry.note)
        for (log in entry.logs) {
            add(typeNames[log.entryTypeId])
            log.values.values.forEach(::add)
        }
        for (trigger in entry.triggers) {
            add(trigger.name)
            add(trigger.note)
        }
        return normalize(parts.joinToString("\n"))
    }

    /** The entry has a log of [typeId] (when given) and its text contains every word of [query]. */
    fun matches(entry: Entry, query: String, typeId: String?, typeNames: Map<String, String>, translate: (String) -> String): Boolean {
        if (typeId != null && entry.logs.none { it.entryTypeId == typeId }) return false
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val text = text(entry, typeNames, translate)
        return words.all { it in text }
    }
}
