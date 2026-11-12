package com.triangulation.locator.solver

/**
 * Reads a typed position, for example from a radio report. Accepts:
 * - decimal degrees: `49.2827, -123.1207` or `49.2827 -123.1207`
 * - with hemisphere letters before or after: `49.2827 N 123.1207 W`, `N49.2827 W123.1207`
 * - degrees and minutes, or degrees, minutes and seconds, with symbols:
 *   `49°16.96' N 123°07.24' W`, `49°16'58"N 123°7'15"W`
 *
 * Latitude comes first unless hemisphere letters say otherwise. Returns null if the text
 * can't be read or is out of range. Pure Kotlin.
 */
object CoordinateParser {
    private const val NUM = """(\d+(?:\.\d+)?)"""
    private const val DMS = """(-)?$NUM\s*(?:°\s*(?:$NUM\s*'\s*(?:$NUM\s*"\s*)?)?)?"""
    private val prefixed = Regex("""([NSEW])\s*$DMS""")
    private val suffixed = Regex("""$DMS\s*([NSEW])?""")

    private class Part(val value: Double, val hemisphere: Char?)

    fun parse(text: String): LatLon? {
        val t = text.trim().uppercase()
            .replace('º', '°').replace('′', '\'').replace('’', '\'').replace('″', '"').replace("''", "\"")
        if (t.isEmpty()) return null
        val letterFirst = t.first() in "NSEW"
        val regex = if (letterFirst) prefixed else suffixed
        val matches = regex.findAll(t).toList()
        if (matches.size != 2) return null

        // Nothing but spaces, commas or semicolons may be left between and around the two parts.
        var rest = t
        for (m in matches.reversed()) rest = rest.removeRange(m.range)
        if (rest.any { !it.isWhitespace() && it != ',' && it != ';' }) return null

        val parts = matches.map { m ->
            val g = m.groupValues
            // Group layout: prefixed = letter, minus, deg, min, sec; suffixed = minus, deg, min, sec, letter.
            val (minus, deg, min, sec, letter) =
                if (letterFirst) listOf(g[2], g[3], g[4], g[5], g[1]) else listOf(g[1], g[2], g[3], g[4], g[5])
            val minutes = min.toDoubleOrNull() ?: 0.0
            val seconds = sec.toDoubleOrNull() ?: 0.0
            if (minutes >= 60 || seconds >= 60) return null
            val hemisphere = letter.firstOrNull()
            if (hemisphere != null && minus.isNotEmpty()) return null // "-49 N" is contradictory
            var value = deg.toDouble() + minutes / 60 + seconds / 3600
            if (minus.isNotEmpty() || hemisphere == 'S' || hemisphere == 'W') value = -value
            Part(value, hemisphere)
        }

        val (a, b) = parts
        val aIsLon = a.hemisphere == 'E' || a.hemisphere == 'W'
        val bIsLon = b.hemisphere == 'E' || b.hemisphere == 'W'
        val aIsLat = a.hemisphere == 'N' || a.hemisphere == 'S'
        val bIsLat = b.hemisphere == 'N' || b.hemisphere == 'S'
        if ((aIsLon && bIsLon) || (aIsLat && bIsLat)) return null
        val (lat, lon) = if (aIsLon || bIsLat) b.value to a.value else a.value to b.value
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        return LatLon(lat, lon)
    }
}
