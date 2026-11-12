package com.triangulation.locator.car

import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks the car-mode maths. Run like SolverTest: right-click the `test` folder. */
class CarMathTest {
    private val second = 1_000_000_000L
    private val fast = 20.0 // m/s, 72 km/h

    private fun fix(t: Int, course: Double, speed: Double = fast, accuracy: Double? = 1.0) =
        CourseFix(t * second, course, speed, accuracy)

    private fun problem(fixes: List<CourseFix>, nowS: Int) =
        CarMath.courseProblem(fixes, nowS * second, CarMath.MIN_LINE_UP_SPEED_MPS)

    @Test
    fun lineUpTurnsGyroHeadingsIntoTrueHeadings() {
        // Car heading 350° true while the gyroscope says 20°.
        val offset = CarMath.lineUpOffset(350.0, 20.0)
        assertEquals(330.0, offset, 1e-9)
        val lineUp = LineUp(offset, 0, runId = 1, courseAccuracyDeg = 1.0)
        // Phone turns 25° right of the car's direction: gyroscope 45° → true 15°.
        assertEquals(15.0, CarMath.trueHeading(45.0, lineUp), 1e-9)
    }

    @Test
    fun straightFastDrivingIsGoodForLiningUp() {
        assertNull(problem(listOf(fix(0, 90.0), fix(1, 90.5), fix(2, 89.8)), nowS = 2))
        // Across north: 359° and 1° are only 2° apart.
        assertNull(problem(listOf(fix(0, 359.5), fix(1, 0.5), fix(2, 0.0)), nowS = 2))
    }

    @Test
    fun badConditionsAreReported() {
        assertEquals(CourseProblem.NoGps, problem(emptyList(), nowS = 0))
        assertEquals(CourseProblem.GpsOld, problem(listOf(fix(0, 90.0), fix(1, 90.0)), nowS = 10))
        assertEquals(CourseProblem.TooSlow, problem(listOf(fix(0, 90.0), fix(1, 90.0, speed = 2.0)), nowS = 1))
        assertEquals(CourseProblem.PoorDirection, problem(listOf(fix(0, 90.0), fix(1, 90.0, accuracy = 8.0)), nowS = 1))
        assertEquals(CourseProblem.Settling, problem(listOf(fix(1, 90.0)), nowS = 1))
        assertEquals(CourseProblem.Turning, problem(listOf(fix(0, 80.0), fix(1, 85.0), fix(2, 90.0)), nowS = 2))
        // An old turn outside the 3-second window doesn't count.
        assertNull(problem(listOf(fix(0, 45.0), fix(4, 90.0), fix(5, 90.0)), nowS = 5))
    }

    @Test
    fun recentCourseAveragesAcrossNorth() {
        val course = CarMath.recentCourseDeg(listOf(fix(0, 359.0), fix(1, 1.0)))
        assertEquals(0.0, if (course > 180) course - 360 else course, 1e-9)
    }

    @Test
    fun errorBandGrowsWithLineUpAge() {
        assertEquals(2.0, CarMath.defaultErrorDeg(0.0, null), 1e-9)
        assertEquals(5.0, CarMath.defaultErrorDeg(180.0, null), 1e-9) // 2 + 3 min x 1°/min
        assertEquals(5.0, CarMath.defaultErrorDeg(60.0, 4.0), 1e-9) // poor GPS direction + 1 min
        assertEquals(30.0, CarMath.defaultErrorDeg(3600.0, null), 1e-9)
    }

    @Test
    fun positionIsMovedAlongTheDirectionOfTravel() {
        val start = LatLon(45.0, -120.0)
        val moved = CarMath.extrapolate(start, speedMps = 25.0, courseDeg = 90.0, seconds = 2.0)
        val v = LocalProjection(start).toVec(moved)
        assertEquals(50.0, v.x, 0.1) // 50 m east
        assertEquals(0.0, v.y, 0.1)
        // Limited to 3 s, so a very stale fix can't throw the position far.
        val capped = LocalProjection(start).toVec(CarMath.extrapolate(start, 25.0, 0.0, 60.0))
        assertEquals(75.0, capped.y, 0.1)
    }

    @Test
    fun errorStatsTrackAverageSpreadAndWorst() {
        val stats = ErrorStats() + 2.0 + (-2.0) + 4.0 + 359.0 // 359° is -1°
        assertEquals(4, stats.count)
        assertEquals(4.0, stats.worstDeg, 1e-9)
        assertEquals(0.75, stats.meanDeg!!, 0.01)
        assertEquals(-1.0, stats.lastDeg!!, 1e-9)
        assertEquals(2.39, stats.spreadDeg!!, 0.05) // same as the ordinary standard deviation
    }
}
