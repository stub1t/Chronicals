package io.github.stub1t.chronicals.ui

/** Path data of one SVG path on the 24 grid, with its opacity. */
class P(val d: String, val opacity: Float = 1f)

/** A built-in entry type icon: soft fill at 22 %, 2 px round strokes and solid details. Generated from src/lib/ui/icons/duo.ts. */
class DuoIconData(val fill: List<P>, val stroke: List<P>, val solid: List<P>)

object DuoIcons {
    val ALL: Map<String, DuoIconData> = mapOf(
        "headache" to DuoIconData(fill = listOf(P("M7 21.5v-3.2C5.2 17 4 14.9 4 12.5 4 8.6 7.1 5.5 11 5.5c3.3 0 6 2.2 6.7 5.3l1.2 2.9c.2.4-.1.8-.5.8h-1v2.1a1.8 1.8 0 0 1-1.8 1.8h-1.9v3.1z")), stroke = listOf(P("M7 21.5v-3.2C5.2 17 4 14.9 4 12.5 4 8.6 7.1 5.5 11 5.5c3.3 0 6 2.2 6.7 5.3l1.2 2.9c.2.4-.1.8-.5.8h-1v2.1a1.8 1.8 0 0 1-1.8 1.8h-1.9v3.1"), P("M13 2.2v1.4M17.4 3.2l-1 1.2M20.6 6.6l-1.4.8")), solid = listOf()),
        "stomachache" to DuoIconData(fill = listOf(P("M8.5 6.8c0 1.4 1 2.2 2.4 2.2 4.4 0 7.7 2.9 7.7 6.7 0 3.4-2.9 5.8-6.8 5.8-3.3 0-6.1-1.5-6.1-4 0-1.8 1.3-2.8 3-2.8 1.6 0 2.4-.9 2.4-2.2z")), stroke = listOf(P("M8.5 3v3.8c0 1.4 1 2.2 2.4 2.2 4.4 0 7.7 2.9 7.7 6.7 0 3.4-2.9 5.8-6.8 5.8-3.3 0-6.1-1.5-6.1-4 0-1.8 1.3-2.8 3-2.8 1.6 0 2.4-.9 2.4-2.2"), P("M5.9 17.7 3 18.6"), P("M17 2.4v1.6M20.3 4.1l-1.1 1.1M21.6 7.8H20")), solid = listOf()),
        "allergy" to DuoIconData(fill = listOf(P("M12 3.2a3.3 3.3 0 0 1 3.1 4.4 3.3 3.3 0 0 1 1 6.4 3.3 3.3 0 0 1-4.1 2.6 3.3 3.3 0 0 1-4.1-2.6 3.3 3.3 0 0 1 1-6.4A3.3 3.3 0 0 1 12 3.2z")), stroke = listOf(P("M12 3.2a3.3 3.3 0 0 1 3.1 4.4 3.3 3.3 0 0 1 1 6.4 3.3 3.3 0 0 1-4.1 2.6 3.3 3.3 0 0 1-4.1-2.6 3.3 3.3 0 0 1 1-6.4A3.3 3.3 0 0 1 12 3.2z"), P("M12 16.6v5.4M12 20c-1.3-1.4-3-1.8-4.6-1.4M12 20c1.3-1.4 3-1.8 4.6-1.4")), solid = listOf(P("M10 10.2a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z"), P("M18.8 3.6a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z"), P("M2.5999999999999996 5a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z"), P("M19.400000000000002 16a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0z"))),
        "blood-sugar" to DuoIconData(fill = listOf(P("M12 2.5S5.5 9.3 5.5 14.4a6.5 6.5 0 0 0 13 0C18.5 9.3 12 2.5 12 2.5z")), stroke = listOf(P("M12 2.5S5.5 9.3 5.5 14.4a6.5 6.5 0 0 0 13 0C18.5 9.3 12 2.5 12 2.5z")), solid = listOf(P("M10.3 9.6h3.4v4h2.7L12 19l-4.4-5.4h2.7z"))),
        "meal" to DuoIconData(fill = listOf(P("M6.7 12a5.3 5.3 0 1 0 10.6 0a5.3 5.3 0 1 0 -10.6 0z")), stroke = listOf(P("M6.7 12a5.3 5.3 0 1 0 10.6 0a5.3 5.3 0 1 0 -10.6 0z"), P("M3 3v5a1.6 1.6 0 0 0 3.2 0V3M4.6 3v18"), P("M21 15h-2.6V8.5C18.4 5.3 19.8 3.3 21 3v18")), solid = listOf()),
        "medication" to DuoIconData(fill = listOf(P("M13.8 4.6a4.9 4.9 0 0 1 6.9 6.9l-3.6 3.6-6.9-6.9z")), stroke = listOf(P("M10.2 8.2 13.8 4.6a4.9 4.9 0 0 1 6.9 6.9l-9.2 9.2a4.9 4.9 0 0 1-6.9-6.9z"), P("M10.2 8.2l6.9 6.9")), solid = listOf(P("M10.2 8.2l-5.6 5.6a4.9 4.9 0 0 0 6.9 6.9l5.6-5.6z", 0.55f))),
        "period" to DuoIconData(fill = listOf(P("M10.5 4S4.5 10.6 4.5 15a6 6 0 0 0 12 0c0-4.4-6-11-6-11z")), stroke = listOf(P("M10.5 4S4.5 10.6 4.5 15a6 6 0 0 0 12 0c0-4.4-6-11-6-11z"), P("M7.7 15.4a2.8 2.8 0 0 0 2.8 2.8")), solid = listOf(P("M18.6 2s-2.6 2.8-2.6 4.8a2.6 2.6 0 0 0 5.2 0c0-2-2.6-4.8-2.6-4.8z"))),
        "note" to DuoIconData(fill = listOf(P("M6 2.5h8.5L19 7v13a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 20V4a1.5 1.5 0 0 1 1-1.5z")), stroke = listOf(P("M14.5 2.5H6.5A1.5 1.5 0 0 0 5 4v16a1.5 1.5 0 0 0 1.5 1.5h11A1.5 1.5 0 0 0 19 20V7z"), P("M14.5 2.5V7H19"), P("M8.5 12h7M8.5 15.5h7M8.5 19h4")), solid = listOf()),
    )

    /** Built-in entry type id to its icon. */
    val BY_TYPE = mapOf(
        "builtin-headache" to "headache",
        "builtin-stomachache" to "stomachache",
        "builtin-allergy" to "allergy",
        "builtin-hypo" to "blood-sugar",
        "builtin-meal" to "meal",
        "builtin-period" to "period",
        "builtin-note" to "note",
    )
}
