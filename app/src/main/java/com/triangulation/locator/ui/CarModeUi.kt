package com.triangulation.locator.ui

import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.triangulation.locator.car.CarMath
import com.triangulation.locator.car.CourseFix
import com.triangulation.locator.car.CourseProblem
import com.triangulation.locator.car.LineUp
import com.triangulation.locator.sensors.GyroState
import com.triangulation.locator.sensors.circularMeanDeg
import com.triangulation.locator.sensors.circularSpreadDeg
import com.triangulation.locator.sensors.gyroUpdates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Car-mode state kept while the app is running, so it survives a trip to the History screen.
 * It is not saved: car mode is always off when the app starts.
 */
object CarSession {
    var enabled by mutableStateOf(false)
    var lineUp by mutableStateOf<LineUp?>(null)
}

/** How long the gyroscope is averaged over when lining up. */
private const val LINE_UP_MS = 1_000L
/** Max wobble of the phone while lining up, degrees. */
private const val LINE_UP_MAX_SPREAD_DEG = 1.5

/**
 * The gyroscope stream, shared so the live readout, line-up and capture all see the same
 * sensor run (and so the same zero direction). Runs only while something collects it.
 */
@Composable
fun rememberSharedGyroFlow(): Flow<GyroState> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        context.getSystemService(SensorManager::class.java).gyroUpdates()
            // Forget the last reading when stopped: a restarted sensor has a new zero.
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), replay = 1)
    }
}

/** GPS direction of travel and speed, or null if this fix doesn't have them. */
fun Location.toCourseFix(): CourseFix? =
    if (hasBearing() && hasSpeed()) {
        CourseFix(
            elapsedNanos = elapsedRealtimeNanos,
            courseDeg = bearing.toDouble(),
            speedMps = speed.toDouble(),
            courseAccuracyDeg = if (hasBearingAccuracy()) bearingAccuracyDegrees.toDouble() else null,
        )
    } else {
        null
    }

/** The last few GPS fixes with a direction of travel, oldest first. */
@Composable
fun rememberCourseFixes(location: Location?): SnapshotStateList<CourseFix> {
    val fixes = remember { mutableStateListOf<CourseFix>() }
    LaunchedEffect(location) {
        val fix = location?.toCourseFix() ?: return@LaunchedEffect
        if (fixes.lastOrNull()?.elapsedNanos == fix.elapsedNanos) return@LaunchedEffect
        fixes.add(fix)
        while (fixes.size > 10) fixes.removeAt(0)
    }
    return fixes
}

sealed interface LineUpResult {
    data class Done(val lineUp: LineUp) : LineUpResult
    data class Failed(val message: String) : LineUpResult
}

/**
 * Lines the gyroscope up with the GPS direction of travel. The camera must be pointing straight
 * along the car (crosshair on a distant point straight down the road). Takes about a second.
 */
suspend fun performLineUp(gyroFlow: Flow<GyroState>, fixes: () -> List<CourseFix>): LineUpResult {
    val minSpeed = CarMath.MIN_LINE_UP_SPEED_MPS
    CarMath.courseProblem(fixes(), SystemClock.elapsedRealtimeNanos(), minSpeed)?.let {
        return LineUpResult.Failed(courseProblemText(it, minSpeed))
    }
    val samples = mutableListOf<GyroState.Reading>()
    withTimeoutOrNull(LINE_UP_MS) {
        gyroFlow.filterIsInstance<GyroState.Reading>().collect { samples += it }
    }
    if (samples.isEmpty()) return LineUpResult.Failed("The gyroscope isn't ready yet. Try again.")
    val runId = samples.last().runId
    val headings = samples.filter { it.runId == runId }.map { it.rawRelativeHeadingDeg }
    if (circularSpreadDeg(headings) > LINE_UP_MAX_SPREAD_DEG) {
        return LineUpResult.Failed("Hold the phone still while lining up, then try again.")
    }
    val now = SystemClock.elapsedRealtimeNanos()
    val current = fixes()
    // Check again: the car may have started turning during that second.
    CarMath.courseProblem(current, now, minSpeed)?.let {
        return LineUpResult.Failed(courseProblemText(it, minSpeed))
    }
    val offset = CarMath.lineUpOffset(CarMath.recentCourseDeg(current), circularMeanDeg(headings))
    return LineUpResult.Done(LineUp(offset, now, runId, current.last().courseAccuracyDeg))
}

fun courseProblemText(problem: CourseProblem, minSpeedMps: Double): String = when (problem) {
    CourseProblem.NoGps -> "Waiting for GPS direction of travel. It appears once the car is moving."
    CourseProblem.GpsOld -> "The GPS position is out of date. The phone needs a view of the sky."
    CourseProblem.TooSlow ->
        "Go faster than ${(minSpeedMps * 3.6).roundToInt()} km/h: GPS can't tell the direction of travel when slow."
    CourseProblem.PoorDirection -> "The GPS direction of travel isn't accurate enough yet. Keep driving."
    CourseProblem.Settling -> "Keep driving straight for a few seconds."
    CourseProblem.Turning -> "Wait for a straight stretch: the car is turning."
}

/** E.g. "0:45" or "12:03". */
fun minutesSeconds(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0).roundToInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
fun CarSafetyDialog(onAccept: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Car mode: passengers only") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Never use the app while driving. Car mode is for a passenger.")
                Text(
                    "In a car the compass is thrown off by the metal around it, so car mode uses the " +
                        "gyroscope instead, lined up with the direction the car is travelling (from GPS)."
                )
                Text(
                    "To line up: on a straight road, put the crosshair on a distant point straight ahead " +
                        "down the road and tap Line up. Line up again every few minutes."
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("I'm a passenger") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
