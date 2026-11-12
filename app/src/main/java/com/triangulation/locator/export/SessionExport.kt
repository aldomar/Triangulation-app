package com.triangulation.locator.export

import com.triangulation.locator.data.Observation
import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import com.triangulation.locator.solver.SolveResult
import com.triangulation.locator.solver.SolveWarning
import com.triangulation.locator.solver.Sightline
import com.triangulation.locator.solver.Solver
import com.triangulation.locator.solver.Vec
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** File types a session can be exported as. */
enum class ExportFormat(val label: String, val extension: String) {
    GeoJson("GeoJSON", "geojson"),
    Gpx("GPX", "gpx"),
}

/**
 * Turns a session's ticked bearings and the estimated location into a GeoJSON or GPX file,
 * for opening in other map apps (e.g. QGIS, OsmAnd, Google Earth, caltopo).
 *
 * Pure Kotlin with no Android code, so it is unit-tested on the computer. Photos are not
 * included: neither format can hold them.
 */
object SessionExport {

    /** [observations] should be oldest first: they are labelled A, B, C… in that order. */
    fun build(format: ExportFormat, sessionName: String, observations: List<Observation>, exportedAtMillis: Long): String =
        when (format) {
            ExportFormat.GeoJson -> geoJson(sessionName, observations, exportedAtMillis)
            ExportFormat.Gpx -> gpx(sessionName, observations, exportedAtMillis)
        }

    /** A safe file name for the export, e.g. "Smoke north of town.geojson". */
    fun fileName(sessionName: String, format: ExportFormat): String {
        val safe = sessionName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(60)
        return "${safe.ifEmpty { "Triangulation" }}.${format.extension}"
    }

    // ---------- Shared geometry ----------

    private class Solved(
        val observations: List<Observation>,
        val result: SolveResult,
        /** Start and end of each sightline, in the same order as [observations]. */
        val sightlines: List<Pair<LatLon, LatLon>>,
    ) {
        val fix get() = result as? SolveResult.Fix
    }

    private fun solve(observations: List<Observation>): Solved {
        val lines = observations.map { Sightline(LatLon(it.latitude, it.longitude), it.bearingDeg, it.errorDeg) }
        val result = Solver.solve(lines)
        if (lines.isEmpty()) return Solved(observations, result, emptyList())
        val proj = LocalProjection.around(lines.map { it.position })
        val sightlines = lines.mapIndexed { i, line ->
            val end = proj.toVec(line.position) + Vec.ofBearing(line.bearingDeg) * Solver.drawLengthM(result, i)
            line.position to proj.toLatLon(end)
        }
        return Solved(observations, result, sightlines)
    }

    fun label(index: Int): String = ('A' + index).toString()

    private fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    /** Rounded decimal without trailing zeros, e.g. 123.4. Null for NaN/infinite values. */
    private fun num(value: Double, decimals: Int): String? =
        if (value.isFinite()) BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        else null

    private fun coord(value: Double): String = num(value, 7) ?: "0"

    private fun compassLabel(accuracy: Int) = when (accuracy) {
        3 -> "high"
        2 -> "medium"
        1 -> "low"
        else -> "unreliable"
    }

    private fun warningLabel(w: SolveWarning) = when (w) {
        SolveWarning.ShallowAngle -> "sightlines cross at a shallow angle"
        SolveWarning.Far -> "target over ${(Solver.FAR_M / 1000).toInt()} km away"
        SolveWarning.UncertaintyUnbounded -> "uncertainty area could not be closed"
        SolveWarning.BearingsDisagree -> "bearings disagree"
    }

    // ---------- GeoJSON (RFC 7946) ----------

    /** A JSON value written as-is (numbers, arrays). Anything else is written as a string. */
    private class Raw(val text: String)

    private fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append(String.format("\\u%04x", c.code))
            else -> append(c)
        }
        append('"')
    }

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(",", "{", "}") { (key, value) ->
            val v = when (value) {
                null -> "null"
                is Raw -> value.text
                is Boolean, is Int, is Long -> value.toString()
                is Double -> num(value, 6) ?: "null"
                is List<*> -> value.joinToString(",", "[", "]") { jsonString(it.toString()) }
                else -> jsonString(value.toString())
            }
            "${jsonString(key)}:$v"
        }

    private fun number(value: Double?, decimals: Int): Raw? = value?.let { num(it, decimals) }?.let(::Raw)

    private fun position(p: LatLon) = "[${coord(p.lon)},${coord(p.lat)}]"

    private fun feature(geometry: String, properties: String) =
        """{"type":"Feature","geometry":$geometry,"properties":$properties}"""

    fun geoJson(sessionName: String, observations: List<Observation>, exportedAtMillis: Long): String {
        val s = solve(observations)
        val features = mutableListOf<String>()

        s.observations.forEachIndexed { i, o ->
            features += feature(
                """{"type":"Point","coordinates":${position(LatLon(o.latitude, o.longitude))}}""",
                jsonObject(
                    "kind" to "observer",
                    "label" to label(i),
                    "time" to iso(o.timeMillis),
                    "bearingDeg" to number(o.bearingDeg, 2),
                    "errorDeg" to number(o.errorDeg, 1),
                    "gpsAccuracyM" to number(o.gpsAccuracyM, 1),
                    "altitudeM" to number(o.altitudeM, 1),
                    "compassAccuracy" to compassLabel(o.compassAccuracy),
                    "distanceToEstimateM" to number(s.fix?.distancesM?.get(i), 0),
                ),
            )
        }
        s.sightlines.forEachIndexed { i, (start, end) ->
            features += feature(
                """{"type":"LineString","coordinates":[${position(start)},${position(end)}]}""",
                jsonObject(
                    "kind" to "sightline",
                    "label" to label(i),
                    "bearingDeg" to number(s.observations[i].bearingDeg, 2),
                    "errorDeg" to number(s.observations[i].errorDeg, 1),
                ),
            )
        }
        s.fix?.let { fix ->
            features += feature(
                """{"type":"Point","coordinates":${position(fix.point)}}""",
                jsonObject(
                    "kind" to "estimate",
                    "label" to "Estimated location",
                    "uncertaintyRadiusM" to number(fix.uncertaintyRadiusM, 0),
                    "bestCrossingAngleDeg" to number(fix.bestCrossingAngleDeg, 1),
                    "rmsMissM" to number(fix.rmsMissM, 0),
                    "bearingsUsed" to s.observations.size,
                    "warnings" to fix.warnings.map(::warningLabel),
                ),
            )
            fix.uncertaintyOutline?.takeIf { it.size >= 3 }?.let { outline ->
                val ring = counterClockwise(outline).let { it + it.first() }
                features += feature(
                    """{"type":"Polygon","coordinates":[[${ring.joinToString(",") { position(it) }}]]}""",
                    jsonObject("kind" to "uncertainty", "label" to "Uncertainty area"),
                )
            }
        }

        val meta = jsonObject(
            "name" to sessionName,
            "exportedAt" to iso(exportedAtMillis),
            "app" to "Triangulation Locator",
            "result" to when (val r = s.result) {
                is SolveResult.Fix -> "fix"
                is SolveResult.NoFix -> "no fix: ${r.reason}"
            },
        )
        return """{"type":"FeatureCollection","properties":$meta,"features":[""" +
            features.joinToString(",\n") + "]}\n"
    }

    /** GeoJSON outer rings should run counter-clockwise (RFC 7946 §3.1.6). */
    private fun counterClockwise(ring: List<LatLon>): List<LatLon> {
        var twiceArea = 0.0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            twiceArea += a.lon * b.lat - b.lon * a.lat
        }
        return if (twiceArea < 0) ring.reversed() else ring
    }

    // ---------- GPX 1.1 ----------

    private fun xml(s: String): String = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> if (c >= ' ' || c == '\n' || c == '\t') append(c)
        }
    }

    private fun latLonAttrs(p: LatLon) = """lat="${coord(p.lat)}" lon="${coord(p.lon)}""""

    fun gpx(sessionName: String, observations: List<Observation>, exportedAtMillis: Long): String {
        val s = solve(observations)
        val fix = s.fix
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<gpx version="1.1" creator="Triangulation Locator" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
            append("  <metadata><name>${xml(sessionName)}</name><time>${iso(exportedAtMillis)}</time></metadata>\n")

            // Waypoints: each observer, then the estimate.
            s.observations.forEachIndexed { i, o ->
                val desc = buildString {
                    append("Bearing ${num(o.bearingDeg, 1)}° ±${num(o.errorDeg, 0)}°")
                    o.gpsAccuracyM?.let { append(", GPS ±${num(it, 0)} m") }
                    append(", compass ${compassLabel(o.compassAccuracy)}")
                    fix?.let { append(", ${num(it.distancesM[i], 0)} m to estimate") }
                }
                append("  <wpt ${latLonAttrs(LatLon(o.latitude, o.longitude))}>")
                o.altitudeM?.let { num(it, 1) }?.let { append("<ele>$it</ele>") }
                append("<time>${iso(o.timeMillis)}</time>")
                append("<name>Observer ${label(i)}</name><desc>${xml(desc)}</desc></wpt>\n")
            }
            if (fix != null) {
                val desc = buildString {
                    append(fix.uncertaintyRadiusM?.let { "Uncertainty: within about ${num(it, 0)} m" } ?: "Uncertainty: too large to draw")
                    append(". Best crossing angle ${num(fix.bestCrossingAngleDeg, 0)}°")
                    append(". From ${s.observations.size} bearings")
                    if (fix.warnings.isNotEmpty()) append(". Warnings: ${fix.warnings.joinToString(", ", transform = ::warningLabel)}")
                    append(".")
                }
                append("  <wpt ${latLonAttrs(fix.point)}><name>Estimated location</name><desc>${xml(desc)}</desc></wpt>\n")
            }

            // Routes: one straight sightline per observer.
            s.sightlines.forEachIndexed { i, (start, end) ->
                append("  <rte><name>Sightline ${label(i)} (${num(s.observations[i].bearingDeg, 1)}°)</name>")
                append("<rtept ${latLonAttrs(start)}/><rtept ${latLonAttrs(end)}/></rte>\n")
            }

            // GPX has no shapes, so the uncertainty area is a closed track around it.
            fix?.uncertaintyOutline?.takeIf { it.size >= 3 }?.let { outline ->
                append("  <trk><name>Uncertainty area</name><trkseg>")
                (outline + outline.first()).forEach { append("<trkpt ${latLonAttrs(it)}/>") }
                append("</trkseg></trk>\n")
            }
            append("</gpx>\n")
        }
    }
}
