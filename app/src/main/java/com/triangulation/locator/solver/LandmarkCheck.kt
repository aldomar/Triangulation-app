package com.triangulation.locator.solver

import com.triangulation.locator.sensors.circularMeanDeg
import com.triangulation.locator.sensors.circularSpreadDeg
import com.triangulation.locator.sensors.wrap180
import kotlin.math.abs
import kotlin.math.atan

/**
 * Compass check against a landmark whose position is known: compares the bearing the phone
 * measured with the true bearing worked out from the two positions. Measures and records only;
 * it never changes bearings. Pure Kotlin.
 */
object LandmarkCheck {
    /** Closer than this, small GPS or map errors change the true bearing noticeably. */
    const val MIN_GOOD_DISTANCE_M = 1_000.0

    /** A difference this large more likely means the wrong landmark or a wrong position. */
    const val IMPLAUSIBLE_DIFFERENCE_DEG = 20.0

    data class Comparison(
        /** True bearing from the observer to the landmark. */
        val expectedBearingDeg: Double,
        /** Measured − expected, folded into -180..180. Positive = compass reads clockwise of true. */
        val differenceDeg: Double,
        val distanceM: Double,
        /** How much of the difference the GPS position error alone could explain, ± degrees. Null if unknown. */
        val gpsExplainsDeg: Double?,
    ) {
        val tooClose: Boolean get() = distanceM < MIN_GOOD_DISTANCE_M
        val implausible: Boolean get() = abs(differenceDeg) > IMPLAUSIBLE_DIFFERENCE_DEG
    }

    fun compare(observer: LatLon, landmark: LatLon, measuredBearingDeg: Double, gpsAccuracyM: Double?): Comparison {
        val expected = initialBearingDeg(observer, landmark)
        val distance = distanceM(observer, landmark)
        return Comparison(
            expectedBearingDeg = expected,
            differenceDeg = wrap180(measuredBearingDeg - expected),
            distanceM = distance,
            gpsExplainsDeg = gpsAccuracyM?.takeIf { distance > 0 }?.let { Math.toDegrees(atan(it / distance)) },
        )
    }

    /** "reads 3.2° clockwise of true", "reads 1.0° anticlockwise of true" or "matches true north". */
    fun describe(differenceDeg: Double): String {
        val size = String.format(java.util.Locale.US, "%.1f", abs(differenceDeg))
        return when {
            size == "0.0" -> "matches true north"
            differenceDeg > 0 -> "reads $size° clockwise of true"
            else -> "reads $size° anticlockwise of true"
        }
    }

    data class Summary(val count: Int, val meanDeg: Double, val spreadDeg: Double)

    /** Average and spread of several checks' differences. Null with no checks. */
    fun summary(differencesDeg: List<Double>): Summary? {
        if (differencesDeg.isEmpty()) return null
        return Summary(differencesDeg.size, wrap180(circularMeanDeg(differencesDeg)), circularSpreadDeg(differencesDeg))
    }
}
