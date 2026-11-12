package com.triangulation.locator.solver

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** A point on the local flat grid, in metres: x = east, y = north. */
data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(k: Double) = Vec(x * k, y * k)
    infix fun dot(o: Vec) = x * o.x + y * o.y
    val length: Double get() = hypot(x, y)

    companion object {
        /** Unit vector pointing along a compass bearing (0 = north, 90 = east). */
        fun ofBearing(bearingDeg: Double): Vec {
            val r = Math.toRadians(bearingDeg)
            return Vec(sin(r), cos(r))
        }
    }
}

/**
 * Flat east/north grid centred on [origin] (equirectangular projection).
 * Accurate to well under 0.1% within about 50 km of the origin, which is plenty for
 * compass bearings that are only good to a few degrees.
 */
class LocalProjection(val origin: LatLon) {
    private val metresPerDegLat = Math.toRadians(1.0) * EARTH_RADIUS_M
    private val metresPerDegLon = metresPerDegLat * cos(Math.toRadians(origin.lat))

    fun toVec(p: LatLon) = Vec((p.lon - origin.lon) * metresPerDegLon, (p.lat - origin.lat) * metresPerDegLat)

    fun toLatLon(v: Vec) = LatLon(origin.lat + v.y / metresPerDegLat, origin.lon + v.x / metresPerDegLon)

    companion object {
        const val EARTH_RADIUS_M = 6_371_008.8

        /** Grid centred on the average of the given points. */
        fun around(points: List<LatLon>) = LocalProjection(
            LatLon(points.map { it.lat }.average(), points.map { it.lon }.average())
        )
    }
}

/** Great-circle distance between two points, metres (haversine). */
fun distanceM(from: LatLon, to: LatLon): Double {
    val p1 = Math.toRadians(from.lat)
    val p2 = Math.toRadians(to.lat)
    val dp = p2 - p1
    val dl = Math.toRadians(to.lon - from.lon)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * LocalProjection.EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
}

/** True bearing from [from] towards [to] at the start of the great-circle path, 0..360. */
fun initialBearingDeg(from: LatLon, to: LatLon): Double {
    val p1 = Math.toRadians(from.lat)
    val p2 = Math.toRadians(to.lat)
    val dl = Math.toRadians(to.lon - from.lon)
    val y = sin(dl) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
    val deg = Math.toDegrees(atan2(y, x))
    return (deg % 360 + 360) % 360
}
