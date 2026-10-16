package io.github.stub1t.chronicals

import io.github.stub1t.chronicals.data.DayTrigger
import io.github.stub1t.chronicals.data.Entry
import io.github.stub1t.chronicals.data.Log
import io.github.stub1t.chronicals.data.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as src/lib/search.test.ts. */
class SearchTest {
    private val german = mapOf("Headache" to "Kopfschmerz", "temples" to "Schläfen", "Stress" to "Stress", "nausea" to "Übelkeit")
    private val translate = { text: String -> german[text] ?: text }
    private val entry = Entry(
        "e1", "2026-09-21", "Long day at the Café", null, null, null, null, null,
        logs = listOf(Log("l1", "e1", "builtin-headache", "2026-09-21T10:00:00Z", null,
            mapOf("intensity" to 4, "location" to "temples", "symptoms" to listOf("nausea")), 4.0)),
        triggers = listOf(DayTrigger("t1", "trig-stress", "Stress", "activity", null, true)),
    )
    private fun match(query: String, typeId: String? = null) =
        Search.matches(entry, query, typeId, mapOf("builtin-headache" to "Headache"), translate)

    @Test fun findsStoredAndTranslatedNames() {
        assertTrue(match("headache")); assertTrue(match("Kopfschmerz")); assertTrue(match("schlafen")); assertTrue(match("übelkeit"))
    }
    @Test fun searchesNotesAndTriggersIgnoringCaseAndAccents() { assertTrue(match("cafe")); assertTrue(match("STRESS")) }
    @Test fun needsEveryWord() { assertTrue(match("headache temples")); assertFalse(match("headache forehead")) }
    @Test fun filtersByType() { assertTrue(match("", "builtin-headache")); assertFalse(match("", "builtin-allergy")) }
    @Test fun ignoresNumbersInFormValues() { assertFalse(match("4")) }
    @Test fun dropsAccentsAndCase() { assertEquals("ubelkeit cafe", Search.normalize("Übelkeit Café")) }
}
