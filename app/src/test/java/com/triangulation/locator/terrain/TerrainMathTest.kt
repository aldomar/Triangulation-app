package com.triangulation.locator.terrain

import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

/** Single-photo (terrain) maths. Run like SolverTest: right-click the `test` folder. */
class TerrainMathTest {
    private val observer = LatLon(45.0, -120.0)
    private val flat = ElevationLookup { 0.0 }

    @Test
    fun centreOfPhotoLooksAlongTheCameraTilt() {
        assertEquals(-2.0, TerrainMath.elevationAngleDeg(0.5, 0.5, 0.8, 4.0 / 3, -2.0), 1e-9)
        assertEquals(3.5, TerrainMath.elevationAngleDeg(0.5, 0.5, 0.8, 4.0 / 3, 3.5), 1e-9)
    }

    @Test
    fun tappingAboveTheCentreLooksHigher() {
        // Camera level; tap a quarter of the photo height above the centre.
        val up = 0.25 * 4.0 / 3
        val expected = Math.toDegrees(atan(up / 0.8))
        assertEquals(expected, TerrainMath.elevationAngleDeg(0.5, 0.25, 0.8, 4.0 / 3, 0.0), 1e-9)
        // Below the centre looks lower.
        assertTrue(TerrainMath.elevationAngleDeg(0.5, 0.75, 0.8, 4.0 / 3, 0.0) < 0)
    }

    @Test
    fun destinationMovesTheRightDistance() {
        val north = TerrainMath.destination(observer, 0.0, 1000.0)
        assertEquals(observer.lat + Math.toDegrees(1000.0 / LocalProjection.EARTH_RADIUS_M), north.lat, 1e-9)
        assertEquals(observer.lon, north.lon, 1e-9)
        val east = TerrainMath.destination(observer, 90.0, 1000.0)
        assertTrue(east.lon > observer.lon)
    }

    @Test
    fun flatGroundHitAllowsForCurvature() {
        // Eye 100 m above flat ground, looking 1° down.
        val hit = TerrainMath.findGround(observer, 0.0, 100.0, -1.0, flat) as GroundHit.Hit
        // Solve 100 - d·tan(1°) - d²(1-k)/2R = 0 for d.
        val a = (1 - TerrainMath.REFRACTION_K) / (2 * LocalProjection.EARTH_RADIUS_M)
        val b = tan(Math.toRadians(1.0))
        val expected = (-b + sqrt(b * b + 4 * a * 100.0)) / (2 * a)
        assertEquals(expected, hit.distanceM, 1.0)
        assertTrue(expected < 100.0 / b) // Curvature brings it nearer than on a flat Earth.
        assertEquals(0.0, hit.groundElevM, 1e-9)
    }

    @Test
    fun lookingUpNeverMeetsFlatGround() {
        assertEquals(GroundHit.NoHit, TerrainMath.findGround(observer, 0.0, 100.0, 0.5, flat))
    }

    @Test
    fun missingDataIsReported() {
        assertEquals(GroundHit.Missing, TerrainMath.findGround(observer, 0.0, 100.0, -1.0, ElevationLookup { null }))
        assertEquals(TerrainResult.NoData(), TerrainMath.estimate(observer, 0.0, 3.0, -1.0, 1.0, ElevationLookup { null }))
    }

    @Test
    fun hillInTheWayIsHitFirst() {
        // A 50 m ridge from 2 km to 2.5 km north; flat ground (0 m) otherwise.
        val ridge = ElevationLookup { p ->
            val d = (p.lat - observer.lat) * Math.toRadians(1.0) * LocalProjection.EARTH_RADIUS_M
            if (d in 2000.0..2500.0) 50.0 else 0.0
        }
        // Eye at 1.5 m, looking just above level: only the ridge stops it.
        val hit = TerrainMath.findGround(observer, 0.0, 1.5, 0.5, ridge) as GroundHit.Hit
        assertEquals(2000.0, hit.distanceM, TerrainMath.STEP_M)
    }

    @Test
    fun estimateGivesNearAndFarFromTiltError() {
        // Eye 1.5 m above flat ground, looking 1° down, tilt ±0.5°.
        val r = TerrainMath.estimate(observer, 0.0, 3.0, -1.0, 0.5, flat) as TerrainResult.Found
        assertEquals(1.5, r.eyeElevM, 1e-9)
        assertEquals(1.5 / tan(Math.toRadians(1.0)), r.distanceM, 1.0)
        assertTrue(r.nearM < r.distanceM)
        val far = r.farM
        assertNotNull(far)
        assertTrue(far!! > r.distanceM)
        assertTrue(r.outline.size >= 4)
    }

    @Test
    fun nearlyLevelSightIsOpenEnded() {
        // Looking 0.05° down: +1° tilt error looks above the horizon, so no far limit.
        val r = TerrainMath.estimate(observer, 0.0, 3.0, -0.05, 1.0, flat) as TerrainResult.Found
        assertNull(r.farM)
        assertTrue(r.veryUncertain)
    }

    @Test
    fun aboveTheHorizonGivesNoGround() {
        val r = TerrainMath.estimate(observer, 0.0, 3.0, 2.0, 1.0, flat)
        assertTrue(r is TerrainResult.NoGround)
    }

    // ---------- Terrarium tiles ----------

    @Test
    fun terrariumDecoding() {
        assertEquals(0.0, Terrarium.elevationFromRgb(128, 0, 0), 1e-9)
        assertEquals(1000.5, Terrarium.elevationFromRgb(131, 232, 128), 1e-9)
        assertEquals(128.0, Terrarium.globalPixel(LatLon(0.0, 0.0), 0).first, 1e-9)
        assertEquals(128.0, Terrarium.globalPixel(LatLon(0.0, 0.0), 0).second, 1e-9)
    }

    @Test
    fun gridSmoothsBetweenPixels() {
        // One zoom-0 tile whose height equals the column number.
        val tile = FloatArray(256 * 256) { i -> (i % 256).toFloat() }
        val grid = TerrariumGrid(zoom = 0) { tile }
        fun lonAtPixelX(x: Double) = x / 256.0 * 360.0 - 180.0
        assertEquals(100.0, grid.elevationM(LatLon(0.0, lonAtPixelX(100.5)))!!, 1e-6)
        assertEquals(100.5, grid.elevationM(LatLon(0.0, lonAtPixelX(101.0)))!!, 1e-6)
    }

    @Test
    fun gridReportsTheMissingTile() {
        val grid = TerrariumGrid { null }
        assertNull(grid.elevationM(observer))
        val missing = grid.missingTile
        assertNotNull(missing)
        assertEquals(Terrarium.ZOOM, missing!!.z)
        // Asking a loaded grid gives a height.
        assertEquals(250.0, TerrariumGrid { FloatArray(256 * 256) { 250f } }.elevationM(observer)!!, 1e-6)
    }
}
