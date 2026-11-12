package com.triangulation.locator.terrain

import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** Ground height lookup, metres above sea level. Null if the height there isn't available (yet). */
fun interface ElevationLookup {
    fun elevationM(p: LatLon): Double?
}

/** Where one line of sight first meets the ground. */
sealed interface GroundHit {
    data class Hit(val distanceM: Double, val point: LatLon, val groundElevM: Double) : GroundHit

    /** The line stays above the ground all the way out to the maximum range. */
    data object NoHit : GroundHit

    /** Height data is missing somewhere along the line. */
    data object Missing : GroundHit
}

/** Single-photo estimate for one bearing: follow the line of sight down to the ground. */
sealed interface TerrainResult {
    data class Found(
        /** Best estimate: where the line of sight meets the ground. */
        val point: LatLon,
        val distanceM: Double,
        /** Nearest likely distance (camera tilt error looking further down). */
        val nearM: Double,
        /** Furthest likely distance, or null if that line never meets the ground within [maxRangeM]. */
        val farM: Double?,
        val maxRangeM: Double,
        /** Ground height at the estimate, metres above sea level. */
        val targetElevM: Double,
        /** Ground height where the observer stood, from the elevation data. */
        val observerGroundM: Double,
        /** Height the line of sight starts from (ground + eye height). */
        val eyeElevM: Double,
        /** Angle of the line of sight above the horizon (negative = looking down). */
        val elevationAngleDeg: Double,
        val tiltErrorDeg: Double,
        /** Outline of the likely area: near–far along the sightline, ± the bearing error across it. */
        val outline: List<LatLon>,
    ) : TerrainResult {
        /** Looking nearly level, so the distance is very uncertain. */
        val veryUncertain: Boolean get() = farM == null || farM > 3 * nearM
    }

    /** The line of sight never meets the ground within [maxRangeM] (e.g. tapped above the horizon). */
    data class NoGround(val elevationAngleDeg: Double, val maxRangeM: Double) : TerrainResult

    /** No elevation data: not downloaded yet ([downloadsOff]) or the download failed. */
    data class NoData(val downloadsOff: Boolean = false) : TerrainResult
}

/**
 * Single-photo maths: the camera tilt and the tapped point give the angle of the line of sight
 * below the horizon. Following that line from the observer's eye until it drops below the
 * ground (from elevation data) gives a distance along the bearing.
 *
 * Allows for the Earth's curvature and normal atmospheric refraction. Pure Kotlin, unit-tested.
 */
object TerrainMath {
    const val MAX_RANGE_M = 40_000.0
    const val STEP_M = 15.0
    /** Start a little in front of the observer so the slope they stand on doesn't count. */
    const val MIN_DISTANCE_M = 30.0
    /** Eye height above the ground. The elevation data gives the ground; GPS height is too rough. */
    const val EYE_HEIGHT_M = 1.5
    /** Standard refraction coefficient: light bends slightly round the Earth. */
    const val REFRACTION_K = 0.13
    const val DEFAULT_TILT_ERROR_DEG = 1.0
    val TILT_ERROR_CHOICES = listOf(0.5, 1.0, 2.0)

    private const val R = LocalProjection.EARTH_RADIUS_M

    /**
     * Angle of the line to the tapped point above the horizon, degrees (negative = below).
     * Same camera model as the tap offset (`BearingMath.tapOffsetDeg`); roll is ignored.
     *
     * @param tapX, tapY tap position as fractions of photo width/height (0.5 = centre)
     * @param focalFraction focal length in pixels divided by photo width
     * @param heightOverWidth photo height / width
     * @param pitchDeg camera tilt above the horizon
     */
    fun elevationAngleDeg(
        tapX: Double,
        tapY: Double,
        focalFraction: Double,
        heightOverWidth: Double,
        pitchDeg: Double,
    ): Double {
        val right = tapX - 0.5
        val up = (0.5 - tapY) * heightOverWidth
        val forward = focalFraction
        val p = Math.toRadians(pitchDeg)
        val vertical = up * cos(p) + forward * sin(p)
        val horizontalForward = forward * cos(p) - up * sin(p)
        return Math.toDegrees(atan2(vertical, hypot(horizontalForward, right)))
    }

    /**
     * Height above sea level of the line of sight at [distanceM], as seen against the curved
     * ground (curvature drop reduced by refraction).
     */
    fun rayHeightM(startElevM: Double, angleDeg: Double, distanceM: Double): Double =
        startElevM + distanceM * tan(Math.toRadians(angleDeg)) -
            distanceM * distanceM * (1 - REFRACTION_K) / (2 * R)

    /** The point [distanceM] from [start] along [bearingDeg] (great circle). */
    fun destination(start: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
        val phi1 = Math.toRadians(start.lat)
        val lambda1 = Math.toRadians(start.lon)
        val theta = Math.toRadians(bearingDeg)
        val delta = distanceM / R
        val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
        val lambda2 = lambda1 + atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sin(phi2))
        var lon = Math.toDegrees(lambda2)
        lon = ((lon + 540.0) % 360.0) - 180.0
        return LatLon(Math.toDegrees(phi2), lon)
    }

    /** Walks out along the line of sight and returns where it first goes below the ground. */
    fun findGround(
        start: LatLon,
        bearingDeg: Double,
        eyeElevM: Double,
        angleDeg: Double,
        lookup: ElevationLookup,
        maxRangeM: Double = MAX_RANGE_M,
        stepM: Double = STEP_M,
    ): GroundHit {
        var prevD = 0.0
        var prevGap = Double.NaN
        var d = MIN_DISTANCE_M
        while (d <= maxRangeM) {
            val p = destination(start, bearingDeg, d)
            val ground = lookup.elevationM(p) ?: return GroundHit.Missing
            val gap = rayHeightM(eyeElevM, angleDeg, d) - ground
            if (gap <= 0) {
                // Refine between the last step above the ground and this one.
                val hitD = if (prevGap.isNaN()) d else prevD + (d - prevD) * prevGap / (prevGap - gap)
                val hitPoint = destination(start, bearingDeg, hitD)
                val hitGround = lookup.elevationM(hitPoint) ?: ground
                return GroundHit.Hit(hitD, hitPoint, hitGround)
            }
            prevD = d
            prevGap = gap
            d += stepM
        }
        return GroundHit.NoHit
    }

    /**
     * The full estimate for one bearing: the best point, plus near and far distances from the
     * camera tilt ± [tiltErrorDeg]. Looking further down always meets the ground sooner.
     */
    fun estimate(
        observer: LatLon,
        bearingDeg: Double,
        bearingErrorDeg: Double,
        angleDeg: Double,
        tiltErrorDeg: Double,
        lookup: ElevationLookup,
        maxRangeM: Double = MAX_RANGE_M,
    ): TerrainResult {
        val ground = lookup.elevationM(observer) ?: return TerrainResult.NoData()
        val eye = ground + EYE_HEIGHT_M
        val centre = when (val h = findGround(observer, bearingDeg, eye, angleDeg, lookup, maxRangeM)) {
            is GroundHit.Hit -> h
            GroundHit.NoHit -> return TerrainResult.NoGround(angleDeg, maxRangeM)
            GroundHit.Missing -> return TerrainResult.NoData()
        }
        val nearM = when (val h = findGround(observer, bearingDeg, eye, angleDeg - tiltErrorDeg, lookup, maxRangeM)) {
            is GroundHit.Hit -> h.distanceM
            GroundHit.NoHit -> centre.distanceM
            GroundHit.Missing -> return TerrainResult.NoData()
        }
        val farM = when (val h = findGround(observer, bearingDeg, eye, angleDeg + tiltErrorDeg, lookup, maxRangeM)) {
            is GroundHit.Hit -> h.distanceM
            GroundHit.NoHit -> null
            GroundHit.Missing -> return TerrainResult.NoData()
        }
        return TerrainResult.Found(
            point = centre.point,
            distanceM = centre.distanceM,
            nearM = nearM,
            farM = farM,
            maxRangeM = maxRangeM,
            targetElevM = centre.groundElevM,
            observerGroundM = ground,
            eyeElevM = eye,
            elevationAngleDeg = angleDeg,
            tiltErrorDeg = tiltErrorDeg,
            outline = sector(observer, bearingDeg, bearingErrorDeg, nearM, farM ?: maxRangeM),
        )
    }

    /** A ring-segment shape: from [nearM] to [farM] along the bearing, ± [halfWidthDeg] across it. */
    fun sector(observer: LatLon, bearingDeg: Double, halfWidthDeg: Double, nearM: Double, farM: Double, steps: Int = 8): List<LatLon> {
        val half = max(halfWidthDeg, 0.3)
        val angles = (0..steps).map { k -> bearingDeg - half + 2 * half * k / steps }
        val nearArc = angles.map { destination(observer, it, nearM) }
        val farArc = angles.reversed().map { destination(observer, it, farM) }
        return nearArc + farArc
    }
}
