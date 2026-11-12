package com.triangulation.locator.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkCheckTest {
    private val origin = LatLon(45.0, -120.0)

    @Test
    fun greatCircleDistanceAndBearing() {
        // One degree of latitude is about 111.2 km, due north.
        val north = LatLon(46.0, -120.0)
        assertEquals(111_195.0, distanceM(origin, north), 50.0)
        assertEquals(0.0, initialBearingDeg(origin, north), 1e-9)
        assertEquals(180.0, initialBearingDeg(north, origin), 1e-9)
        // Due east along the equator.
        assertEquals(90.0, initialBearingDeg(LatLon(0.0, 10.0), LatLon(0.0, 11.0)), 1e-9)
        assertEquals(270.0, initialBearingDeg(LatLon(0.0, 11.0), LatLon(0.0, 10.0)), 1e-9)
    }

    @Test
    fun agreesWithTheLocalGridNearby() {
        val proj = LocalProjection(origin)
        val target = proj.toLatLon(Vec(3_000.0, 4_000.0)) // 5 km away at 36.87°
        assertEquals(5_000.0, distanceM(origin, target), 5.0)
        assertEquals(36.87, initialBearingDeg(origin, target), 0.05)
    }

    @Test
    fun differenceSignAndWrapping() {
        val proj = LocalProjection(origin)
        val landmark = proj.toLatLon(Vec(0.0, 5_000.0)) // due north
        val c = LandmarkCheck.compare(origin, landmark, measuredBearingDeg = 3.0, gpsAccuracyM = 5.0)
        assertEquals(0.0, c.expectedBearingDeg, 1e-6)
        assertEquals(3.0, c.differenceDeg, 1e-6)
        assertEquals(Math.toDegrees(kotlin.math.atan(5.0 / 5_000.0)), c.gpsExplainsDeg!!, 1e-9)
        assertFalse(c.tooClose)
        assertFalse(c.implausible)

        // 358° measured vs 0° true: 2° anticlockwise, not 358° clockwise.
        assertEquals(-2.0, LandmarkCheck.compare(origin, landmark, 358.0, null).differenceDeg, 1e-6)
        assertNull(LandmarkCheck.compare(origin, landmark, 358.0, null).gpsExplainsDeg)
    }

    @Test
    fun flagsCloseLandmarksAndHugeDifferences() {
        val proj = LocalProjection(origin)
        val near = proj.toLatLon(Vec(0.0, 400.0))
        assertTrue(LandmarkCheck.compare(origin, near, 0.0, 5.0).tooClose)
        assertTrue(LandmarkCheck.compare(origin, near, 30.0, 5.0).implausible)
    }

    @Test
    fun describesTheDifference() {
        assertEquals("reads 3.2° clockwise of true", LandmarkCheck.describe(3.21))
        assertEquals("reads 1.0° anticlockwise of true", LandmarkCheck.describe(-1.0))
        assertEquals("matches true north", LandmarkCheck.describe(0.02))
    }

    @Test
    fun summaryAveragesAcrossNorth() {
        assertNull(LandmarkCheck.summary(emptyList()))
        val s = LandmarkCheck.summary(listOf(2.0, 4.0, 3.0))!!
        assertEquals(3, s.count)
        assertEquals(3.0, s.meanDeg, 1e-6)
        assertTrue(s.spreadDeg in 0.5..1.5)
        // -179 and 179 average to ±180, not 0.
        assertEquals(180.0, kotlin.math.abs(LandmarkCheck.summary(listOf(-179.0, 179.0))!!.meanDeg), 1e-6)
    }
}
