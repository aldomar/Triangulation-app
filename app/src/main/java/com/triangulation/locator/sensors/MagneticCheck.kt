package com.triangulation.locator.sensors

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Spots magnetic interference by comparing the field strength the phone measures with the
 * strength Earth's field should have at this place (Android's built-in world model).
 *
 * Nearby steel (cars, fences, rebar) and electric currents add their own field, so the total
 * comes out too strong or too weak, and the compass points the wrong way. Pure Kotlin.
 */
object MagneticCheck {
    /** More than this fraction off the expected strength counts as disturbed. A guess, to tune outdoors. */
    const val DISTURBED_FRACTION = 0.15

    /** Measured ÷ expected field strength, or null if either is unknown. 1.0 = exactly as expected. */
    fun ratio(measuredUt: Double?, expectedUt: Double?): Double? =
        if (measuredUt == null || expectedUt == null || expectedUt <= 0.0 || measuredUt <= 0.0) null
        else measuredUt / expectedUt

    fun isDisturbed(ratio: Double?): Boolean = ratio != null && abs(ratio - 1.0) > DISTURBED_FRACTION

    /** For example "32% too strong" or "20% too weak". */
    fun describe(ratio: Double): String {
        val percent = (abs(ratio - 1.0) * 100).roundToInt()
        return if (ratio >= 1.0) "$percent% too strong" else "$percent% too weak"
    }
}
