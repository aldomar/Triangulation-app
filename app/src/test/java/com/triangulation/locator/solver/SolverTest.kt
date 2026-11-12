package com.triangulation.locator.solver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the triangulation maths. Run in Android Studio: right-click the `test` folder
 * and choose "Run 'Tests in ...'". No phone needed.
 */
class SolverTest {
    // Two observers 10 km apart on an east-west line, at latitude 45°.
    private val proj = LocalProjection(LatLon(45.0, -120.0))
    private val west = proj.toLatLon(Vec(-5_000.0, 0.0))
    private val east = proj.toLatLon(Vec(5_000.0, 0.0))

    private fun fix(result: SolveResult): SolveResult.Fix {
        assertTrue("Expected a fix but got $result", result is SolveResult.Fix)
        return result as SolveResult.Fix
    }

    private fun assertNear(expected: Vec, actual: LatLon, toleranceM: Double) {
        val v = proj.toVec(actual)
        val miss = (v - expected).length
        assertTrue("Off by $miss m (expected $expected, got $v)", miss <= toleranceM)
    }

    @Test
    fun twoBearingsCrossAtKnownPoint() {
        // NE from the west observer and NW from the east observer meet 5 km north of the middle.
        val result = fix(Solver.solve(listOf(Sightline(west, 45.0, 3.0), Sightline(east, 315.0, 3.0))))
        assertNear(Vec(0.0, 5_000.0), result.point, 5.0)
        assertEquals(90.0, result.bestCrossingAngleDeg, 1e-6)
        assertEquals(7_071.0, result.distancesM[0], 10.0)
        assertTrue(result.warnings.isEmpty())
        val outline = result.uncertaintyOutline
        assertNotNull(outline)
        assertEquals(4, outline!!.size)
        // ±3° at ~7 km is roughly ±370 m sideways for each line.
        assertTrue(result.uncertaintyRadiusM!! in 300.0..700.0)
    }

    @Test
    fun parallelBearingsHaveNoFix() {
        val result = Solver.solve(listOf(Sightline(west, 0.0, 5.0), Sightline(east, 0.0, 5.0)))
        assertEquals(SolveResult.NoFix(NoFixReason.Parallel), result)
    }

    @Test
    fun linesCrossingBehindObserversHaveNoFix() {
        // Pointing away from each other: the lines only meet behind the observers.
        val result = Solver.solve(listOf(Sightline(west, 225.0, 5.0), Sightline(east, 135.0, 5.0)))
        assertTrue(result is SolveResult.NoFix && result.reason == NoFixReason.BehindObserver)
    }

    @Test
    fun observersInSamePlaceHaveNoFix() {
        val result = Solver.solve(listOf(Sightline(west, 10.0, 5.0), Sightline(west, 80.0, 5.0)))
        assertEquals(SolveResult.NoFix(NoFixReason.ObserversTooClose), result)
    }

    @Test
    fun tooFewBearings() {
        assertEquals(SolveResult.NoFix(NoFixReason.TooFewBearings), Solver.solve(listOf(Sightline(west, 0.0, 5.0))))
    }

    @Test
    fun shallowAngleIsWarned() {
        // Target 20 km north of the midpoint: lines cross at about 28°.
        val result = fix(Solver.solve(listOf(Sightline(west, 14.04, 2.0), Sightline(east, 345.96, 2.0))))
        assertTrue(SolveWarning.ShallowAngle in result.warnings)
        assertNear(Vec(0.0, 20_000.0), result.point, 30.0)
    }

    @Test
    fun wideErrorBandsMakeUnboundedArea() {
        // Shallow crossing (~28°) with ±20° errors: some corner rays never meet.
        val result = fix(Solver.solve(listOf(Sightline(west, 14.04, 20.0), Sightline(east, 345.96, 20.0))))
        assertNull(result.uncertaintyOutline)
        assertTrue(SolveWarning.UncertaintyUnbounded in result.warnings)
    }

    @Test
    fun threeConsistentBearingsMeetAtPoint() {
        val target = Vec(1_000.0, 6_000.0)
        val south = proj.toLatLon(Vec(0.0, -3_000.0))
        val lines = listOf(west, east, south).map { p ->
            val d = target - proj.toVec(p)
            Sightline(p, Math.toDegrees(kotlin.math.atan2(d.x, d.y)), 3.0)
        }
        val result = fix(Solver.solve(lines))
        assertNear(target, result.point, 5.0)
        assertEquals(0.0, result.rmsMissM!!, 5.0)
        assertTrue(SolveWarning.BearingsDisagree !in result.warnings)
        assertNotNull(result.uncertaintyOutline)
    }

    @Test
    fun threeDisagreeingBearingsAreWarned() {
        val target = Vec(0.0, 6_000.0)
        val south = proj.toLatLon(Vec(0.0, -3_000.0))
        val lines = listOf(west, east, south).map { p ->
            val d = target - proj.toVec(p)
            Sightline(p, Math.toDegrees(kotlin.math.atan2(d.x, d.y)), 1.0)
        }.toMutableList()
        // Throw the third bearing off by 15° while claiming ±1°.
        lines[2] = lines[2].copy(bearingDeg = lines[2].bearingDeg + 15.0)
        val result = fix(Solver.solve(lines))
        assertTrue(SolveWarning.BearingsDisagree in result.warnings)
    }

    @Test
    fun crossingAngleFoldsTo90() {
        assertEquals(90.0, Solver.bestCrossingAngle(listOf(0.0, 90.0)), 1e-9)
        assertEquals(10.0, Solver.bestCrossingAngle(listOf(0.0, 190.0)), 1e-9)
        assertEquals(20.0, Solver.bestCrossingAngle(listOf(350.0, 10.0)), 1e-9)
    }

    @Test
    fun projectionRoundTrips() {
        val p = LatLon(45.1234, -119.8765)
        val back = proj.toLatLon(proj.toVec(p))
        assertEquals(p.lat, back.lat, 1e-9)
        assertEquals(p.lon, back.lon, 1e-9)
    }
}
