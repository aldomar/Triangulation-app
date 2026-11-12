package com.triangulation.locator.solver

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** One line of sight: where the observer stood, the true bearing, and its ± error in degrees. */
data class Sightline(val position: LatLon, val bearingDeg: Double, val errorDeg: Double)

sealed interface SolveResult {
    data class Fix(
        val point: LatLon,
        /** Outline of the uncertainty area, or null if it is effectively unlimited. */
        val uncertaintyOutline: List<LatLon>?,
        /** Furthest distance from [point] to the edge of the uncertainty area, metres. */
        val uncertaintyRadiusM: Double?,
        /** Distance from each observer to [point], metres, in input order. */
        val distancesM: List<Double>,
        /** Widest angle between any two sightlines, folded into 0..90°. */
        val bestCrossingAngleDeg: Double,
        /** For 3+ bearings: typical distance the lines miss the point by, metres. */
        val rmsMissM: Double?,
        val warnings: Set<SolveWarning>,
    ) : SolveResult

    data class NoFix(val reason: NoFixReason, val observerIndex: Int? = null) : SolveResult
}

enum class NoFixReason {
    /** Fewer than two bearings. */
    TooFewBearings,
    /** All observers stood in nearly the same place, so the lines can't cross meaningfully. */
    ObserversTooClose,
    /** The sightlines are (nearly) parallel. */
    Parallel,
    /** The best crossing point is behind one of the observers. */
    BehindObserver,
}

enum class SolveWarning {
    /** Best crossing angle under 30°: the point smears along the lines. */
    ShallowAngle,
    /** Over 50 km away, where the flat-grid maths gets less accurate. */
    Far,
    /** The error bands are too wide for the area to close. */
    UncertaintyUnbounded,
    /** 3+ bearings disagree by more than their error bands suggest. */
    BearingsDisagree,
}

/**
 * Finds where sightlines cross.
 *
 * - 2 bearings: exact intersection; the uncertainty area is the four-sided shape formed by
 *   intersecting each bearing ± its error.
 * - 3+ bearings: weighted least-squares point (closest to all lines), with an uncertainty
 *   ellipse from the fit's covariance.
 */
object Solver {
    const val SHALLOW_ANGLE_DEG = 30.0
    const val FAR_M = 50_000.0
    private const val MIN_BASELINE_M = 20.0
    private const val PARALLEL_DEG = 1.0
    private const val ELLIPSE_POINTS = 48

    fun solve(lines: List<Sightline>): SolveResult {
        if (lines.size < 2) return SolveResult.NoFix(NoFixReason.TooFewBearings)

        val proj = LocalProjection.around(lines.map { it.position })
        val origins = lines.map { proj.toVec(it.position) }
        val dirs = lines.map { Vec.ofBearing(it.bearingDeg) }

        val baseline = origins.indices.maxOf { i -> origins.indices.maxOf { j -> (origins[i] - origins[j]).length } }
        if (baseline < MIN_BASELINE_M) return SolveResult.NoFix(NoFixReason.ObserversTooClose)

        val bestAngle = bestCrossingAngle(lines.map { it.bearingDeg })
        if (bestAngle < PARALLEL_DEG) return SolveResult.NoFix(NoFixReason.Parallel)

        // Least squares. Each line's weight is 1 / (sideways error at the target)^2, which
        // depends on the distance to the target, so refine twice from an angle-only start.
        val sigmas = lines.map { Math.toRadians(max(it.errorDeg, 0.1)) }
        var weights = sigmas.map { 1.0 / (it * it) }
        var fit = leastSquares(origins, dirs, weights) ?: return SolveResult.NoFix(NoFixReason.Parallel)
        repeat(2) {
            weights = origins.indices.map { i ->
                val r = max((fit.point - origins[i]).length, 1.0)
                1.0 / (sigmas[i] * r).let { it * it }
            }
            fit = leastSquares(origins, dirs, weights) ?: return SolveResult.NoFix(NoFixReason.Parallel)
        }
        val x = fit.point

        // The target must be in front of every observer.
        for (i in lines.indices) {
            if ((x - origins[i]) dot dirs[i] <= 0.0) return SolveResult.NoFix(NoFixReason.BehindObserver, i)
        }

        val distances = origins.map { (x - it).length }
        val warnings = mutableSetOf<SolveWarning>()
        if (bestAngle < SHALLOW_ANGLE_DEG) warnings += SolveWarning.ShallowAngle
        if (distances.max() > FAR_M) warnings += SolveWarning.Far

        val outline: List<Vec>?
        var rmsMiss: Double? = null
        if (lines.size == 2) {
            outline = quadrilateral(origins, lines)
        } else {
            // Perpendicular miss distances and the chi-square of the fit.
            val misses = origins.indices.map { i -> cross(dirs[i], x - origins[i]) }
            rmsMiss = sqrt(misses.sumOf { it * it } / misses.size)
            val chi2PerDof = misses.indices.sumOf { weights[it] * misses[it] * misses[it] } / (lines.size - 2)
            if (chi2PerDof > 4.0) warnings += SolveWarning.BearingsDisagree
            // If the lines disagree more than their error bands say, widen the area to match.
            outline = ellipse(x, fit.covariance, max(1.0, chi2PerDof))
        }
        if (outline == null) warnings += SolveWarning.UncertaintyUnbounded

        return SolveResult.Fix(
            point = proj.toLatLon(x),
            uncertaintyOutline = outline?.map(proj::toLatLon),
            uncertaintyRadiusM = outline?.maxOf { (it - x).length },
            distancesM = distances,
            bestCrossingAngleDeg = bestAngle,
            rmsMissM = rmsMiss,
            warnings = warnings,
        )
    }

    /**
     * How long to draw sightline [index] on a map or in an export: a bit past the target,
     * or 20 km if there is no fix.
     */
    fun drawLengthM(result: SolveResult, index: Int): Double {
        val fix = result as? SolveResult.Fix ?: return 20_000.0
        val d = fix.distancesM[index]
        return max(d * 1.4, d + 2 * (fix.uncertaintyRadiusM ?: 0.0))
    }

    /** Widest angle between any two bearings, as a line crossing angle in 0..90°. */
    fun bestCrossingAngle(bearingsDeg: List<Double>): Double {
        var best = 0.0
        for (i in bearingsDeg.indices) for (j in i + 1 until bearingsDeg.size) {
            val diff = abs(bearingsDeg[i] - bearingsDeg[j]) % 180.0
            best = max(best, if (diff > 90.0) 180.0 - diff else diff)
        }
        return best
    }

    /**
     * Where two rays cross, if they cross in front of both starting points.
     * Used for the corners of the two-bearing uncertainty area.
     */
    fun intersectRays(p1: Vec, b1Deg: Double, p2: Vec, b2Deg: Double): Vec? {
        val d1 = Vec.ofBearing(b1Deg)
        val d2 = Vec.ofBearing(b2Deg)
        val denom = cross(d1, d2)
        if (abs(denom) < 1e-9) return null
        val diff = p2 - p1
        val t1 = cross(diff, d2) / denom
        val t2 = cross(diff, d1) / denom
        if (t1 <= 0 || t2 <= 0) return null
        return p1 + d1 * t1
    }

    private class Fit(val point: Vec, val covariance: DoubleArray)

    /**
     * Point minimising the weighted sum of squared perpendicular distances to the lines.
     * Returns null if the system is singular (all lines parallel).
     */
    private fun leastSquares(origins: List<Vec>, dirs: List<Vec>, weights: List<Double>): Fit? {
        var a11 = 0.0; var a12 = 0.0; var a22 = 0.0; var b1 = 0.0; var b2 = 0.0
        for (i in origins.indices) {
            // Normal to the line (perpendicular to its direction).
            val nx = dirs[i].y
            val ny = -dirs[i].x
            val w = weights[i]
            val c = nx * origins[i].x + ny * origins[i].y
            a11 += w * nx * nx; a12 += w * nx * ny; a22 += w * ny * ny
            b1 += w * nx * c; b2 += w * ny * c
        }
        val det = a11 * a22 - a12 * a12
        if (abs(det) <= 1e-12 * (a11 + a22) * (a11 + a22)) return null
        // Inverse of the normal matrix = covariance of the estimate.
        val inv = doubleArrayOf(a22 / det, -a12 / det, a11 / det) // (c11, c12, c22)
        val x = inv[0] * b1 + inv[1] * b2
        val y = inv[1] * b1 + inv[2] * b2
        return Fit(Vec(x, y), inv)
    }

    /** Corners from intersecting each bearing ± its error, in drawing order. */
    private fun quadrilateral(origins: List<Vec>, lines: List<Sightline>): List<Vec>? {
        val (a, b) = lines
        val corners = listOf(1.0 to 1.0, 1.0 to -1.0, -1.0 to -1.0, -1.0 to 1.0).map { (sa, sb) ->
            intersectRays(
                origins[0], a.bearingDeg + sa * a.errorDeg,
                origins[1], b.bearingDeg + sb * b.errorDeg,
            ) ?: return null
        }
        return corners
    }

    /** Covariance ellipse (scaled by [scale] on the variance) as a polygon. */
    private fun ellipse(centre: Vec, cov: DoubleArray, scale: Double): List<Vec> {
        val (c11, c12, c22) = cov.map { it * scale }
        // Eigen-decomposition of the 2x2 symmetric matrix.
        val mean = (c11 + c22) / 2
        val diff = sqrt(((c11 - c22) / 2).let { it * it } + c12 * c12)
        val major = sqrt(max(mean + diff, 0.0))
        val minor = sqrt(max(mean - diff, 0.0))
        val angle = 0.5 * atan2(2 * c12, c11 - c22) // direction of the major axis from +x (east)
        val ca = cos(angle)
        val sa = sin(angle)
        return (0 until ELLIPSE_POINTS).map { k ->
            val t = 2 * Math.PI * k / ELLIPSE_POINTS
            val u = major * cos(t)
            val v = minor * sin(t)
            centre + Vec(u * ca - v * sa, u * sa + v * ca)
        }
    }

    private fun cross(a: Vec, b: Vec) = a.x * b.y - a.y * b.x
}
