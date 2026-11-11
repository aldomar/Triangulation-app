package com.triangulation.locator.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Which way the back camera points, and how the phone is tilted. */
sealed interface OrientationState {
    /** The phone has no rotation-vector sensor (no compass), so heading can't be measured. */
    data object Unavailable : OrientationState

    data object Waiting : OrientationState

    data class Reading(
        /** Direction the back camera points, degrees clockwise from magnetic north, 0..360. */
        val magneticHeadingDeg: Double,
        /** Camera tilt above (+) or below (-) the horizon, degrees. */
        val pitchDeg: Double,
        /** Sideways tilt, degrees. Positive = right edge lower. */
        val rollDeg: Double,
        /** One of SensorManager.SENSOR_STATUS_* (UNRELIABLE, ACCURACY_LOW/MEDIUM/HIGH). */
        val accuracy: Int,
        /** The sensor's own heading error estimate, degrees, if the phone provides one. */
        val headingErrorDeg: Double?,
        /** Unsmoothed values from this one sensor event, used for averaging at capture time. */
        val rawMagneticHeadingDeg: Double = magneticHeadingDeg,
        val rawPitchDeg: Double = pitchDeg,
        val rawRollDeg: Double = rollDeg,
    ) : OrientationState
}

/**
 * Live compass heading and tilt from Android's fused rotation-vector sensor.
 *
 * Heading is measured for the back camera's line of sight with the phone held upright in
 * portrait, which is how photos will be taken in later phases.
 *
 * The sensors run only while the flow is collected, at the UI rate (about 15 per second).
 */
fun SensorManager.orientationUpdates(): Flow<OrientationState> {
    val rotationSensor = getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: return flowOf(OrientationState.Unavailable)
    val magnetometer = getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    return callbackFlow {
        trySend(OrientationState.Waiting)

        val rotation = FloatArray(9)
        val smoother = AngleSmoother()
        var rotationAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        var magnetometerAccuracy: Int? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                // Some older devices crash if given more than 4 values here.
                SensorManager.getRotationMatrixFromVector(rotation, event.values.copyOf(4))

                // rotation maps phone axes to world axes (x = east, y = north, z = up).
                // The back camera looks along the phone's -Z axis, which is column 2 negated.
                val east = -rotation[2]
                val north = -rotation[5]
                val up = -rotation[8]
                val heading = Math.toDegrees(atan2(east.toDouble(), north.toDouble()))
                val pitch = Math.toDegrees(asin(up.coerceIn(-1f, 1f).toDouble()))
                // Height of the phone's right edge (x axis) vs top edge (y axis).
                val roll = Math.toDegrees(atan2(-rotation[6].toDouble(), rotation[7].toDouble()))

                val headingError = event.values.getOrNull(4)
                    ?.takeIf { it >= 0f }
                    ?.let { Math.toDegrees(it.toDouble()) }

                val smoothed = smoother.update(heading, pitch, roll)
                trySend(
                    OrientationState.Reading(
                        magneticHeadingDeg = normalize360(smoothed[0]),
                        pitchDeg = smoothed[1],
                        rollDeg = smoothed[2],
                        accuracy = magnetometerAccuracy ?: rotationAccuracy,
                        headingErrorDeg = headingError,
                        rawMagneticHeadingDeg = normalize360(heading),
                        rawPitchDeg = pitch,
                        rawRollDeg = roll,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                when (sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> rotationAccuracy = accuracy
                    Sensor.TYPE_MAGNETIC_FIELD -> magnetometerAccuracy = accuracy
                }
            }
        }

        registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        // The magnetometer already runs for the rotation vector, so listening to it costs
        // almost nothing. We only use it for its calibration status, which is more honest
        // than the fused sensor's on many phones.
        magnetometer?.let { registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        awaitClose { unregisterListener(listener) }
    }.conflate()
}

fun normalize360(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

/**
 * Light smoothing so the numbers don't flicker. Angles are averaged as unit vectors,
 * so 359° and 1° average to 0°, not 180°.
 */
private class AngleSmoother(private val alpha: Double = 0.2) {
    private var sums: DoubleArray? = null // sin, cos pairs

    fun update(vararg anglesDeg: Double): DoubleArray {
        val current = sums
        val next = DoubleArray(anglesDeg.size * 2)
        for (i in anglesDeg.indices) {
            val rad = Math.toRadians(anglesDeg[i])
            val s = sin(rad)
            val c = cos(rad)
            next[2 * i] = if (current == null) s else current[2 * i] + alpha * (s - current[2 * i])
            next[2 * i + 1] = if (current == null) c else current[2 * i + 1] + alpha * (c - current[2 * i + 1])
        }
        sums = next
        return DoubleArray(anglesDeg.size) { i -> Math.toDegrees(atan2(next[2 * i], next[2 * i + 1])) }
    }
}
