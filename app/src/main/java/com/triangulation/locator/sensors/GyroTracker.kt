package com.triangulation.locator.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf

/**
 * Which way the back camera points according to the gyroscope (plus accelerometer for tilt),
 * WITHOUT the magnetometer. Unaffected by a car's metal and wiring, but its zero direction is
 * arbitrary and drifts slowly, so it must be "lined up" against the GPS direction of travel.
 */
sealed interface GyroState {
    /** No gyroscope on this phone. */
    data object Unavailable : GyroState

    data object Waiting : GyroState

    data class Reading(
        /** Camera direction from the sensor's own arbitrary zero (NOT north), 0..360, smoothed. */
        val relativeHeadingDeg: Double,
        val pitchDeg: Double,
        val rollDeg: Double,
        /**
         * Changes every time the sensor starts. The zero direction may jump between runs,
         * so a line-up is only valid for the run it was made in.
         */
        val runId: Long,
        /** Unsmoothed values from this one event, for averaging at line-up and capture. */
        val rawRelativeHeadingDeg: Double,
        val rawPitchDeg: Double,
        val rawRollDeg: Double,
    ) : GyroState
}

fun SensorManager.hasGyroHeading(): Boolean = getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null

/**
 * Live gyroscope heading from Android's "game rotation vector" sensor. Runs only while
 * collected, at the UI rate (about 15 per second).
 */
fun SensorManager.gyroUpdates(): Flow<GyroState> {
    val sensor = getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: return flowOf(GyroState.Unavailable)

    return callbackFlow {
        trySend(GyroState.Waiting)
        val runId = SystemClock.elapsedRealtimeNanos()
        val rotation = FloatArray(9)
        val smoother = AngleSmoother()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (heading, pitch, roll) = cameraAngles(event.values, rotation)
                val smoothed = smoother.update(heading, pitch, roll)
                trySend(
                    GyroState.Reading(
                        relativeHeadingDeg = normalize360(smoothed[0]),
                        pitchDeg = smoothed[1],
                        rollDeg = smoothed[2],
                        runId = runId,
                        rawRelativeHeadingDeg = normalize360(heading),
                        rawPitchDeg = pitch,
                        rawRollDeg = roll,
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { unregisterListener(listener) }
    }.conflate()
}
