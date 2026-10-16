package io.github.stub1t.chronicals

import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Json
import io.github.stub1t.chronicals.data.Times
import io.github.stub1t.chronicals.ui.day.DayViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** What the native app writes has to read back the same in both apps. */
class FormatsTest {
    @Test fun timesLookLikeJavaScriptToIsoString() {
        assertEquals("2026-10-14T06:40:00.000Z", Times.iso(Instant.parse("2026-10-14T06:40:00Z")))
        assertEquals("2026-10-14T06:40:00.123Z", Times.iso(Instant.parse("2026-10-14T06:40:00.123456Z")))
        assertEquals(Instant.parse("2026-10-14T06:40:00Z"), Times.parse("2026-10-14T06:40:00.000Z"))
    }

    @Test fun metricValuesRoundTrip() {
        val text = """{"intensity":3,"location":"temples","side_effects":["aura","nausea"],"blood_glucose":4.5}"""
        val values = Json.parseObject(text)
        assertEquals(3, values["intensity"])
        assertEquals(listOf("aura", "nausea"), values["side_effects"])
        // Key order is not part of the format (the desktop org.json used here does not keep it; Android's does).
        assertEquals(values, Json.parseObject(Json.stringify(values)))
    }

    @Test fun intensityCacheFollowsTheWebApp() {
        assertEquals(3.0, Diary.intensityOf(mapOf("intensity" to 3))!!, 0.0)
        assertEquals(4.0, Diary.intensityOf(mapOf("severity" to "severe"))!!, 0.0)
        assertNull(Diary.intensityOf(mapOf("type" to "bleeding")))
    }

    @Test fun rankingIsMostUsedFirstAndStable() {
        assertEquals(listOf("b", "a", "c"), DayViewModel.rank(listOf("a", "b", "b", "c", "a", "b")))
        assertEquals(listOf("x", "y"), DayViewModel.rank(listOf("x", "y")))
    }
}
