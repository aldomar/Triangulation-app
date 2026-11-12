package com.triangulation.locator.data

import android.hardware.SensorManager
import com.triangulation.locator.sensors.MagneticCheck
import com.triangulation.locator.terrain.TerrainMath
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object BearingMath {
    /** Used only if the phone doesn't report its camera's focal length. Typical portrait photo width. */
    private const val FALLBACK_HORIZONTAL_FOV_DEG = 50.0

    fun fallbackFocalFraction(): Double = 0.5 / tan(Math.toRadians(FALLBACK_HORIZONTAL_FOV_DEG / 2))

    /**
     * Horizontal angle from the photo centre to a tapped point, in degrees (+ = right).
     *
     * Plan formula: atan((x_tap - x_center) / f_px). This version also accounts for the camera
     * being tilted up or down, which matters when tapping far above or below the centre.
     *
     * @param tapX, tapY tap position as fractions of photo width/height (0.5 = centre)
     * @param focalFraction focal length in pixels divided by photo width
     * @param heightOverWidth photo height / width
     * @param pitchDeg camera tilt above the horizon
     */
    fun tapOffsetDeg(
        tapX: Double,
        tapY: Double,
        focalFraction: Double,
        heightOverWidth: Double,
        pitchDeg: Double,
    ): Double {
        // Ray to the tapped pixel in camera coordinates, scaled so photo width = 1.
        val right = tapX - 0.5
        val up = (0.5 - tapY) * heightOverWidth
        val forward = focalFraction
        // Tilt the ray by the camera pitch and measure its horizontal direction.
        val p = Math.toRadians(pitchDeg)
        val horizontalForward = forward * cos(p) - up * sin(p)
        return Math.toDegrees(atan2(right, horizontalForward))
    }

    /** Angle of a saved bearing's line of sight above the horizon (negative = looking down). */
    fun elevationAngleDeg(obs: Observation): Double = TerrainMath.elevationAngleDeg(
        obs.tapX,
        obs.tapY,
        obs.focalLengthFraction ?: fallbackFocalFraction(),
        if (obs.photoWidth > 0) obs.photoHeight.toDouble() / obs.photoWidth else 4.0 / 3.0,
        obs.pitchDeg,
    )

    /**
     * Starting error band from compass calibration, widened to ±15° when the magnetic field was
     * disturbed ([MagneticCheck]). The user can change it before saving.
     */
    fun defaultErrorDeg(compassAccuracy: Int, magneticFieldRatio: Double? = null): Double {
        val fromCalibration = when (compassAccuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 5.0
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 10.0
            else -> 15.0
        }
        return if (MagneticCheck.isDisturbed(magneticFieldRatio)) max(fromCalibration, 15.0) else fromCalibration
    }
}
