package com.triangulation.locator.car

import com.triangulation.locator.sensors.circularMeanDeg
import com.triangulation.locator.sensors.normalize360
import com.triangulation.locator.sensors.wrap180
import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import com.triangulation.locator.solver.Vec
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Car mode: the bearing's direction comes from the gyroscope, "lined up" once against the
 * GPS direction of travel, instead of the compass (which a car's metal throws off).
 *
 * Pure Kotlin with no Android code, so it is unit-tested on the computer.
 */

/** One GPS reading of the car's movement. Times are Android elapsed-realtime nanoseconds. */
data class CourseFix(
    val elapsedNanos: Long,
    /** Direction of travel, degrees clockwise from true north. */
    val courseDeg: Double,
    val speedMps: Double,
    /** The GPS chip's own estimate of the direction error, if it gives one. */
    val courseAccuracyDeg: Double?,
)

/** Why the GPS direction of travel can't be trusted right now. */
enum class CourseProblem { NoGps, GpsOld, TooSlow, PoorDirection, Settling, Turning }

/** The gyroscope's zero, tied to true north at one moment. */
data class LineUp(
    /** Add to the gyroscope heading to get the true heading. */
    val offsetDeg: Double,
    val atElapsedNanos: Long,
    /** The gyroscope run this belongs to (see GyroState.Reading.runId). */
    val runId: Long,
    val courseAccuracyDeg: Double?,
) {
    fun ageSeconds(nowNanos: Long): Double = (nowNanos - atElapsedNanos) / 1e9
}

object CarMath {
    /** Below this speed GPS direction of travel is unreliable. 15 km/h. */
    const val MIN_LINE_UP_SPEED_MPS = 15 / 3.6
    /** The driving test only counts samples above 20 km/h. */
    const val MIN_TEST_SPEED_MPS = 20 / 3.6
    const val MAX_COURSE_ACCURACY_DEG = 3.0
    /** Max change of direction over the last few seconds to count as "driving straight". */
    const val MAX_TURN_DEG = 2.0
    const val STRAIGHT_WINDOW_S = 3.0
    const val MAX_FIX_AGE_S = 2.0
    /** After this long, ask the user to line up again. */
    const val LINE_UP_STALE_S = 180.0
    /** Assumed gyroscope drift, used for the default error band. To be tuned from driving tests. */
    const val DRIFT_DEG_PER_MIN = 1.0

    /**
     * Whether the GPS direction of travel is good enough to line up against (or to count in
     * the driving test). Null means it's fine.
     *
     * @param fixes recent fixes, oldest first.
     */
    fun courseProblem(fixes: List<CourseFix>, nowNanos: Long, minSpeedMps: Double): CourseProblem? {
        val last = fixes.lastOrNull() ?: return CourseProblem.NoGps
        if ((nowNanos - last.elapsedNanos) / 1e9 > MAX_FIX_AGE_S) return CourseProblem.GpsOld
        if (last.speedMps < minSpeedMps) return CourseProblem.TooSlow
        if (last.courseAccuracyDeg != null && last.courseAccuracyDeg > MAX_COURSE_ACCURACY_DEG) {
            return CourseProblem.PoorDirection
        }
        val recent = recentFixes(fixes, last)
        if (recent.size < 2) return CourseProblem.Settling
        if (recent.any { abs(wrap180(it.courseDeg - last.courseDeg)) > MAX_TURN_DEG }) return CourseProblem.Turning
        return null
    }

    /** Average direction of travel over the last few seconds. */
    fun recentCourseDeg(fixes: List<CourseFix>): Double {
        val last = fixes.last()
        return normalize360(circularMeanDeg(recentFixes(fixes, last).map { it.courseDeg }))
    }

    private fun recentFixes(fixes: List<CourseFix>, last: CourseFix) =
        fixes.filter { (last.elapsedNanos - it.elapsedNanos) / 1e9 <= STRAIGHT_WINDOW_S }

    /** Offset that turns gyroscope headings into true headings, given the camera points along [courseDeg]. */
    fun lineUpOffset(courseDeg: Double, gyroHeadingDeg: Double): Double = normalize360(courseDeg - gyroHeadingDeg)

    fun trueHeading(gyroHeadingDeg: Double, lineUp: LineUp): Double = normalize360(gyroHeadingDeg + lineUp.offsetDeg)

    /** Default ± error band for a car bearing, growing with time since line-up. Whole degrees, 2..30. */
    fun defaultErrorDeg(lineUpAgeSeconds: Double, courseAccuracyDeg: Double?): Double {
        val base = max(2.0, courseAccuracyDeg ?: 2.0)
        val drift = DRIFT_DEG_PER_MIN * max(lineUpAgeSeconds, 0.0) / 60.0
        return (base + drift).roundToInt().toDouble().coerceIn(2.0, 30.0)
    }

    /**
     * Where the car was [seconds] after a GPS fix, moving at [speedMps] along [courseDeg].
     * Used to place the observer at the moment of the photo. Limited to -1..3 s.
     */
    fun extrapolate(position: LatLon, speedMps: Double, courseDeg: Double, seconds: Double): LatLon {
        val dt = seconds.coerceIn(-1.0, 3.0)
        val proj = LocalProjection(position)
        return proj.toLatLon(Vec.ofBearing(courseDeg) * (speedMps * dt))
    }
}

/**
 * Running statistics of a heading error (sensor heading minus GPS direction), for the driving
 * test. Immutable, so it can be held in Compose state.
 */
data class ErrorStats(
    val count: Int = 0,
    private val sumSin: Double = 0.0,
    private val sumCos: Double = 0.0,
    /** Largest error seen, either direction, degrees. */
    val worstDeg: Double = 0.0,
    val lastDeg: Double? = null,
) {
    operator fun plus(errorDeg: Double): ErrorStats {
        val e = wrap180(errorDeg)
        val r = Math.toRadians(e)
        return ErrorStats(count + 1, sumSin + sin(r), sumCos + cos(r), max(worstDeg, abs(e)), e)
    }

    /** Average error (a steady offset, e.g. phone not quite facing forward), degrees. */
    val meanDeg: Double? get() = if (count == 0) null else Math.toDegrees(atan2(sumSin, sumCos))

    /** How much the error wanders around its average (circular standard deviation), degrees. */
    val spreadDeg: Double?
        get() {
            if (count == 0) return null
            val meanLength = (hypot(sumSin, sumCos) / count).coerceIn(1e-12, 1.0)
            return Math.toDegrees(sqrt(-2.0 * ln(meanLength)))
        }
}
