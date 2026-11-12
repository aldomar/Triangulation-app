package com.triangulation.locator.export

import com.triangulation.locator.data.Observation
import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import com.triangulation.locator.solver.Vec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the GeoJSON and GPX files. Run like SolverTest: right-click the `test` folder. */
class SessionExportTest {
    // Same layout as SolverTest: observers 10 km apart, lines meeting 5 km north of the middle.
    private val proj = LocalProjection(LatLon(45.0, -120.0))
    private val west = proj.toLatLon(Vec(-5_000.0, 0.0))
    private val east = proj.toLatLon(Vec(5_000.0, 0.0))
    private val time = 1_762_860_000_000L // 2025-11-11T11:20:00Z

    private fun obs(id: String, at: LatLon, bearing: Double, t: Long) = Observation(
        id = id, sessionId = 1, included = true, timeMillis = t,
        latitude = at.lat, longitude = at.lon, altitudeM = 300.0, gpsAccuracyM = 5.0,
        magneticHeadingDeg = bearing, headingSpreadDeg = 0.5, declinationDeg = 0.0,
        pitchDeg = 0.0, rollDeg = 0.0, compassAccuracy = 3,
        tapX = 0.5, tapY = 0.5, focalLengthFraction = 0.8, tapOffsetDeg = 0.0,
        bearingDeg = bearing, errorDeg = 3.0,
        photoFileName = "$id.jpg", photoWidth = 1920, photoHeight = 1440,
    )

    private val twoBearings = listOf(obs("a", west, 45.0, time), obs("b", east, 315.0, time + 60_000))

    private fun count(text: String, part: String) = text.windowed(part.length).count { it == part }

    @Test
    fun geoJsonHasObserversSightlinesEstimateAndArea() {
        val json = SessionExport.geoJson("Smoke \"north\" \\ test", twoBearings, time)
        assertTrue(json.startsWith("""{"type":"FeatureCollection""""))
        assertEquals(2, count(json, "\"kind\":\"observer\""))
        assertEquals(2, count(json, "\"kind\":\"sightline\""))
        assertEquals(1, count(json, "\"kind\":\"estimate\""))
        assertEquals(1, count(json, "\"type\":\"Polygon\""))
        // Name is escaped, times are ISO 8601 UTC.
        assertTrue(json.contains(""""name":"Smoke \"north\" \\ test""""))
        assertTrue(json.contains("\"time\":\"2025-11-11T11:20:00Z\""))
        // Brackets balance (no broken structure).
        assertEquals(count(json, "{"), count(json, "}"))
        assertEquals(count(json, "["), count(json, "]"))
    }

    @Test
    fun geoJsonEstimateIsWhereTheLinesCross() {
        val json = SessionExport.geoJson("x", twoBearings, time)
        val expected = proj.toLatLon(Vec(0.0, 5_000.0))
        // The estimate point's coordinates follow "kind":"estimate"'s geometry, which comes first in the feature.
        val estimateFeature = json.lines().first { it.contains("\"kind\":\"estimate\"") }
        val coords = Regex(""""coordinates":\[(-?[\d.]+),(-?[\d.]+)]""").find(estimateFeature)!!.groupValues
        assertEquals(expected.lon, coords[1].toDouble(), 1e-4)
        assertEquals(expected.lat, coords[2].toDouble(), 1e-4)
    }

    @Test
    fun geoJsonPolygonRingIsClosedAndCounterClockwise() {
        val json = SessionExport.geoJson("x", twoBearings, time)
        val polygon = json.lines().first { it.contains("\"type\":\"Polygon\"") }
        val points = Regex("""\[(-?[\d.]+),(-?[\d.]+)]""").findAll(polygon)
            .map { LatLon(it.groupValues[2].toDouble(), it.groupValues[1].toDouble()) }.toList()
        assertEquals(points.first(), points.last())
        var twiceArea = 0.0
        for (i in 0 until points.size - 1) {
            twiceArea += points[i].lon * points[i + 1].lat - points[i + 1].lon * points[i].lat
        }
        assertTrue("Ring should be counter-clockwise", twiceArea > 0)
    }

    @Test
    fun singleBearingExportsWithoutEstimate() {
        val json = SessionExport.geoJson("x", twoBearings.take(1), time)
        assertEquals(1, count(json, "\"kind\":\"observer\""))
        assertEquals(1, count(json, "\"kind\":\"sightline\""))
        assertFalse(json.contains("\"kind\":\"estimate\""))
        assertTrue(json.contains("no fix: TooFewBearings"))
    }

    @Test
    fun gpxHasWaypointsRoutesAndAreaTrack() {
        val gpx = SessionExport.gpx("Fire & <smoke>", twoBearings, time)
        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("<name>Fire &amp; &lt;smoke&gt;</name>"))
        assertEquals(3, count(gpx, "<wpt ")) // A, B and the estimate
        assertEquals(2, count(gpx, "<rte>"))
        assertEquals(1, count(gpx, "<trk>"))
        assertTrue(gpx.contains("<name>Estimated location</name>"))
        assertTrue(gpx.contains("<ele>300</ele>"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun carModeBearingsAreLabelled() {
        val car = twoBearings.map { it.copy(headingFromCar = true, compassAccuracy = -1, speedMps = 20.0, lineUpAgeSeconds = 45.0) }
        val json = SessionExport.geoJson("x", car, time)
        assertEquals(2, count(json, "\"headingSource\":\"car (GPS + gyroscope)\""))
        assertEquals(2, count(json, "\"compassAccuracy\":null"))
        assertTrue(json.contains("\"speedKmh\":72"))
        val gpx = SessionExport.gpx("x", car, time)
        assertTrue(gpx.contains("car mode at 72 km/h, lined up 45 s before"))
        // Compass bearings say so too.
        assertEquals(2, count(SessionExport.geoJson("x", twoBearings, time), "\"headingSource\":\"compass\""))
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("a_b_c.gpx", SessionExport.fileName("a/b:c", ExportFormat.Gpx))
        assertEquals("Triangulation.geojson", SessionExport.fileName("  ", ExportFormat.GeoJson))
    }
}
