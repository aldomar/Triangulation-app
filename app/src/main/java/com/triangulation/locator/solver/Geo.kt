package com.triangulation.locator.solver

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

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
