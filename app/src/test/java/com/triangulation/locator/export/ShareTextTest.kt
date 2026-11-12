package com.triangulation.locator.export

import com.triangulation.locator.data.Observation
import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import com.triangulation.locator.solver.NoFixReason
import com.triangulation.locator.solver.Sightline
import com.triangulation.locator.solver.SolveResult
import com.triangulation.locator.solver.Solver
import com.triangulation.locator.solver.Vec
import com.triangulation.locator.terrain.TerrainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Checks the text sent by the map's Share button. */
class ShareTextTest {
    private val proj = LocalProjection(LatLon(45.0, -120.0))
    private val west = proj.toLatLon(Vec(-5_000.0, 0.0))
    private val east = proj.toLatLon(Vec(5_000.0, 0.0))
    private val time = 1_762_860_000_000L // 2025-11-11T11:20:00Z
    private val utc = ZoneId.of("UTC")

    private fun obs(id: String, at: LatLon, bearing: Double, t: Long, error: Double = 3.0) = Observation(
        id = id, sessionId = 1, included = true, timeMillis = t,
        latitude = at.lat, longitude = at.lon, altitudeM = 300.0, gpsAccuracyM = 5.0,
        magneticHeadingDeg = bearing, headingSpreadDeg = 0.5, declinationDeg = 0.0,
        pitchDeg = 0.0, rollDeg = 0.0, compassAccuracy = 3,
        tapX = 0.5, tapY = 0.5, focalLengthFraction = 0.8, tapOffsetDeg = 0.0,
        bearingDeg = bearing, errorDeg = error,
        photoFileName = "$id.jpg", photoWidth = 1920, photoHeight = 1440,
    )

    private fun solve(list: List<Observation>) =
        Solver.solve(list.map { Sightline(LatLon(it.latitude, it.longitude), it.bearingDeg, it.errorDeg) })

    @Test
    fun fixHasCoordinatesUncertaintyTimeAndLink() {
        val list = listOf(obs("a", west, 45.0, time), obs("b", east, 315.0, time + 60_000))
        val result = solve(list) as SolveResult.Fix
        val text = ShareText.build("Ridge smoke", list, result, null, utc)!!

        val lat = String.format(java.util.Locale.US, "%.5f", result.point.lat)
        val lon = String.format(java.util.Locale.US, "%.5f", result.point.lon)
        assertTrue(text, text.startsWith("Ridge smoke: estimated location $lat, $lon, within about "))
        assertTrue(text, text.contains("From 2 bearings, last at 11:21 UTC, 11 Nov 2025."))
        assertTrue(text, text.contains("https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map="))
        assertTrue(text, text.endsWith("not an exact location."))
        assertFalse("observer positions must not be shared", text.contains(String.format(java.util.Locale.US, "%.5f", west.lat)))
        assertFalse(text.contains("caution"))
    }

    @Test
    fun unboundedUncertaintyIsSaidPlainly() {
        // Error bands over 45°: the outer edges point away from each other, so the area can't close.
        val list = listOf(obs("a", west, 45.0, time, error = 50.0), obs("b", east, 315.0, time, error = 50.0))
        val result = solve(list) as SolveResult.Fix
        assertNull(result.uncertaintyRadiusM)
        val text = ShareText.build("S", list, result, null, utc)!!
        assertTrue(text, text.contains("uncertainty too large to measure"))
        assertTrue(text, text.contains("Treat with caution: the object could be much further away."))
    }

    @Test
    fun singleBearingTerrainEstimate() {
        val one = listOf(obs("a", west, 0.0, time))
        val point = proj.toLatLon(Vec(-5_000.0, 2_300.0))
        val found = TerrainResult.Found(
            point = point, distanceM = 2_300.0, nearM = 1_200.0, farM = null, maxRangeM = 40_000.0,
            targetElevM = 500.0, observerGroundM = 800.0, eyeElevM = 801.5, elevationAngleDeg = -5.0,
            tiltErrorDeg = 1.0, outline = emptyList(),
        )
        val text = ShareText.build("S", one, SolveResult.NoFix(NoFixReason.TooFewBearings), listOf(found), utc)!!
        assertTrue(text, text.contains("about 2.3 km from the observer (likely 1.2 km to beyond 40.0 km)"))
        assertTrue(text, text.contains("From 1 bearing and the shape of the land"))
        assertTrue(text, text.contains("very uncertain"))
    }

    @Test
    fun nothingToShareWithoutAResult() {
        val one = listOf(obs("a", west, 0.0, time))
        assertNull(ShareText.build("S", one, SolveResult.NoFix(NoFixReason.TooFewBearings), null, utc))
        assertNull(ShareText.build("S", emptyList(), SolveResult.NoFix(NoFixReason.TooFewBearings), null, utc))
    }

    @Test
    fun zoomSuitsTheUncertainty() {
        assertEquals(12, ShareText.zoomFor(49.0, null))
        assertEquals(14, ShareText.zoomFor(49.28, 400.0))
        assertEquals(17, ShareText.zoomFor(49.0, 10.0))
        assertEquals(5, ShareText.zoomFor(49.0, 5_000_000.0))
    }
}
