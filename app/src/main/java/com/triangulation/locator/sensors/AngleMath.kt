package com.triangulation.locator.sensors

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

fun normalize360(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

/** An angle difference folded into -180..180. */
fun wrap180(deg: Double): Double = normalize360(deg + 180.0) - 180.0

/**
 * Average of angles in degrees, done on the unit circle so that 359° and 1° average to 0°.
 */
fun circularMeanDeg(anglesDeg: List<Double>): Double {
    require(anglesDeg.isNotEmpty())
    var s = 0.0
    var c = 0.0
    for (a in anglesDeg) {
        val r = Math.toRadians(a)
        s += sin(r)
        c += cos(r)
    }
    return Math.toDegrees(atan2(s, c))
}

/**
 * Circular standard deviation in degrees: how much the angles wandered.
 * About the same as an ordinary standard deviation when the spread is small.
 */
fun circularSpreadDeg(anglesDeg: List<Double>): Double {
    require(anglesDeg.isNotEmpty())
    var s = 0.0
    var c = 0.0
    for (a in anglesDeg) {
        val r = Math.toRadians(a)
        s += sin(r)
        c += cos(r)
    }
    val meanLength = (hypot(s, c) / anglesDeg.size).coerceIn(1e-12, 1.0)
    return Math.toDegrees(sqrt(-2.0 * ln(meanLength)))
}
