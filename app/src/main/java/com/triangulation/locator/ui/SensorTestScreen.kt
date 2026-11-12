package com.triangulation.locator.ui

import android.Manifest
import android.hardware.SensorManager
import android.location.Location
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triangulation.locator.car.CarMath
import com.triangulation.locator.car.ErrorStats
import com.triangulation.locator.car.LineUp
import com.triangulation.locator.sensors.GyroState
import com.triangulation.locator.sensors.hasGyroHeading
import com.triangulation.locator.sensors.wrap180
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.triangulation.locator.sensors.GpsState
import com.triangulation.locator.sensors.MagneticCheck
import com.triangulation.locator.sensors.OrientationState
import com.triangulation.locator.sensors.normalize360
import com.triangulation.locator.ui.theme.StatusColors
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/** Live readout of GPS, true heading, tilt and compass calibration. */
@Composable
fun SensorTestScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val permissions = rememberGrantedPermissions(*LOCATION_PERMISSIONS)
    val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION]
    val hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION]

    val orientation by rememberOrientationState()
    val gps by rememberGpsState(enabled = hasFineLocation)
    val location = gps?.location
    val declination = remember(location) { location?.declinationDeg() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader("Sensor check", onBack)
            Text(
                "Make sure GPS and the compass are working before taking bearings. Best results outdoors, away from cars and metal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!hasFineLocation) {
                LocationPermissionCard(
                    approximateOnly = hasCoarseLocation,
                    onRequest = permissions.request,
                    onOpenSettings = { context.openAppSettings() },
                )
            } else {
                GpsCard(gps, onOpenLocationSettings = { context.openLocationSettings() })
            }

            HeadingCard(orientation, declination)
            CompassAccuracyCard(orientation, location)
            TiltCard(orientation)
            DrivingTestCard(orientation, declination, location)

            Text(
                "Your position and bearings never leave this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LocationPermissionCard(approximateOnly: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    InfoCard("Location") {
        Text(
            if (approximateOnly) {
                "The app has \"approximate\" location only. Triangulation needs the exact GPS position. " +
                    "Open settings, tap Permissions → Location, and turn on \"Use precise location\"."
            } else {
                "The app needs location permission to read your GPS position. " +
                    "Your position is only used on this phone and is never sent anywhere."
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequest) { Text("Allow location") }
            if (approximateOnly) Button(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

@Composable
private fun GpsCard(gps: GpsState?, onOpenLocationSettings: () -> Unit) {
    InfoCard("GPS position") {
        when {
            gps == null -> Text("Starting GPS…")
            !gps.providerEnabled -> {
                Text("Location (GPS) is turned off on this phone.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenLocationSettings) { Text("Turn on location") }
            }
            else -> GpsDetails(gps)
        }
    }
}

@Composable
private fun GpsDetails(gps: GpsState) {
    val location = gps.location
    val now = rememberNowTicker()
    val ageSeconds = location?.ageSeconds(now)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (location == null) {
            Text("Waiting for a GPS fix… Go outside with a clear view of the sky. The first fix can take a minute.")
        } else {
            val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accuracy", Modifier.weight(1f))
                if (accuracy != null) {
                    StatusBadge("±${fmt(accuracy, 0)} m", gpsAccuracyColor(accuracy))
                } else {
                    Text("unknown")
                }
            }
            ValueRow("Latitude", "${fmt(location.latitude, 6)}°")
            ValueRow("Longitude", "${fmt(location.longitude, 6)}°")
            ValueRow("Altitude", if (location.hasAltitude()) "${fmt(location.altitude, 0)} m" else "—")
            ValueRow(
                "Last fix",
                DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(location.time)) +
                    (ageSeconds?.let { " ($it s ago)" } ?: ""),
            )
            if (ageSeconds != null && ageSeconds > 10) {
                WarningText("This position is old. Waiting for a fresh fix.")
            }
        }
        ValueRow("Satellites used / seen", "${gps.satellitesUsed} / ${gps.satellitesInView}")
    }
}

@Composable
private fun HeadingCard(orientation: OrientationState, declination: Double?) {
    InfoCard("Heading (direction the back camera points)") {
        when (orientation) {
            OrientationState.Unavailable -> Text("This phone has no compass sensor, so it can't measure bearings.")
            OrientationState.Waiting -> Text("Starting compass…")
            is OrientationState.Reading -> {
                val magnetic = orientation.magneticHeadingDeg
                val trueHeading = declination?.let { normalize360(magnetic + it) }
                val shown = trueHeading ?: magnetic
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${fmt(shown, 0)}°",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        cardinal(shown) + if (trueHeading != null) "  true" else "  magnetic",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                if (trueHeading != null) {
                    ValueRow("True heading", "${fmt(trueHeading, 1)}°")
                }
                ValueRow("Magnetic heading", "${fmt(magnetic, 1)}°")
                ValueRow(
                    "Declination",
                    declination?.let { "${fmt(abs(it), 1)}° ${if (it >= 0) "E" else "W"}" } ?: "needs GPS fix",
                )
                orientation.headingErrorDeg?.let { ValueRow("Sensor's own error estimate", "±${fmt(it, 0)}°") }
                if (declination == null) {
                    WarningText("Showing magnetic heading. True north correction starts once GPS has a fix.")
                }
                if (abs(orientation.pitchDeg) > 60) {
                    WarningText("Hold the phone upright, with the back camera pointing at the object. Heading is unreliable when the phone is flat.")
                }
            }
        }
    }
}

@Composable
private fun CompassAccuracyCard(orientation: OrientationState, location: Location?) {
    if (orientation !is OrientationState.Reading) return
    val (label, color) = compassAccuracyLabel(orientation.accuracy)
    val expectedField = remember(location) { location?.expectedFieldUt() }
    val fieldRatio = MagneticCheck.ratio(orientation.fieldStrengthUt, expectedField)
    InfoCard("Compass calibration") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Status", Modifier.weight(1f))
            StatusBadge(label, color)
        }
        if (orientation.accuracy != SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            Spacer(Modifier.height(8.dp))
            Text(CALIBRATION_TIP, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        ValueRow("Magnetic field measured", orientation.fieldStrengthUt?.let { "${fmt(it, 1)} µT" } ?: "waiting…")
        ValueRow("Expected here", expectedField?.let { "${fmt(it, 1)} µT" } ?: "needs GPS fix")
        if (fieldRatio != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Interference", Modifier.weight(1f))
                if (MagneticCheck.isDisturbed(fieldRatio)) {
                    StatusBadge("Field ${MagneticCheck.describe(fieldRatio)}", StatusColors.Bad)
                } else {
                    StatusBadge("None detected", StatusColors.Good)
                }
            }
            if (MagneticCheck.isDisturbed(fieldRatio)) {
                Text(
                    "Nearby metal or electric current is changing the magnetic field, so the compass may point " +
                        "the wrong way. Move away from cars, metal fences, buildings and power lines.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

const val CALIBRATION_TIP =
    "To calibrate: move away from cars, metal and power lines, then slowly wave the phone " +
        "in a figure-8 shape 3–4 times, turning it as you go."

@Composable
private fun TiltCard(orientation: OrientationState) {
    if (orientation !is OrientationState.Reading) return
    InfoCard("Tilt") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ValueRow("Pitch (camera up/down)", "${signed(orientation.pitchDeg)}°")
                ValueRow("Roll (sideways)", "${signed(orientation.rollDeg)}°")
                val level = abs(orientation.rollDeg) < 2
                Text(
                    if (level) "Phone is level side to side" else "Tilt the phone to level the line",
                    color = if (level) StatusColors.Good else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            LevelIndicator(orientation.pitchDeg, orientation.rollDeg)
        }
    }
}

/** Artificial horizon: the line stays level with the real horizon as the phone tilts. */
@Composable
private fun LevelIndicator(pitchDeg: Double, rollDeg: Double) {
    val outline = MaterialTheme.colorScheme.outline
    val level = abs(rollDeg) < 2
    val lineColor = if (level) StatusColors.Good else MaterialTheme.colorScheme.secondary
    Box(Modifier.size(96.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(outline, radius = r - 2, style = Stroke(width = 2.dp.toPx()))
            // Fixed crosshair = where the camera points.
            drawLine(outline, Offset(c.x - 8, c.y), Offset(c.x + 8, c.y), strokeWidth = 2f)
            drawLine(outline, Offset(c.x, c.y - 8), Offset(c.x, c.y + 8), strokeWidth = 2f)
            // Horizon moves down as the camera tilts up, and rotates against the roll.
            val dy = (pitchDeg / 45.0 * r).toFloat().coerceIn(-r * 0.8f, r * 0.8f)
            rotate(-rollDeg.toFloat(), pivot = c) {
                drawLine(
                    lineColor,
                    Offset(c.x - r * 0.8f, c.y + dy),
                    Offset(c.x + r * 0.8f, c.y + dy),
                    strokeWidth = 4.dp.toPx(),
                )
            }
        }
    }
}

// ---------- Driving test ----------

/**
 * Measures, in a moving car, how far the compass and the lined-up gyroscope are from the GPS
 * direction of travel. The phone must face straight ahead along the car the whole time.
 * The gyroscope runs only after "Start driving test".
 */
@Composable
private fun DrivingTestCard(orientation: OrientationState, declination: Double?, location: Location?) {
    val context = LocalContext.current
    val hasGyro = remember { context.getSystemService(SensorManager::class.java).hasGyroHeading() }
    var running by remember { mutableStateOf(false) }

    InfoCard("Driving test (passenger only)") {
        if (!hasGyro) {
            Text("This phone has no gyroscope, so car mode isn't available on it.")
        } else if (!running) {
            Text(
                "Checks how well bearings can be taken from a moving car. A passenger holds the phone " +
                    "upright and steady, facing straight ahead along the car (a dashboard mount is best). " +
                    "On a straight road tap Line up, then drive normally for 10–15 minutes."
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { running = true }) { Text("Start driving test") }
        } else {
            DrivingTestBody(orientation, declination, location, onStop = { running = false })
        }
    }
}

@Composable
private fun DrivingTestBody(
    orientation: OrientationState,
    declination: Double?,
    location: Location?,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gyroFlow = rememberSharedGyroFlow()
    val gyro by gyroFlow.collectAsStateWithLifecycle(GyroState.Waiting)
    val fixes = rememberCourseFixes(location)
    val now = rememberNowTicker()

    var lineUp by remember { mutableStateOf<LineUp?>(null) }
    var lining by remember { mutableStateOf(false) }
    var compassStats by remember { mutableStateOf(ErrorStats()) }
    var gyroStats by remember { mutableStateOf(ErrorStats()) }

    val reading = gyro as? GyroState.Reading
    val validLineUp = lineUp?.takeIf { reading != null && it.runId == reading.runId }
    val compassTrue = (orientation as? OrientationState.Reading)?.let { o ->
        declination?.let { normalize360(o.magneticHeadingDeg + it) }
    }
    val gyroTrue = if (reading != null && validLineUp != null) CarMath.trueHeading(reading.relativeHeadingDeg, validLineUp) else null
    val lastFix = fixes.lastOrNull()
    val problem = CarMath.courseProblem(fixes.toList(), now, CarMath.MIN_TEST_SPEED_MPS)

    // One sample per new GPS fix, only while driving straight above 20 km/h.
    LaunchedEffect(lastFix) {
        val fix = lastFix ?: return@LaunchedEffect
        if (CarMath.courseProblem(fixes.toList(), SystemClock.elapsedRealtimeNanos(), CarMath.MIN_TEST_SPEED_MPS) != null) {
            return@LaunchedEffect
        }
        compassTrue?.let { compassStats += it - fix.courseDeg }
        gyroTrue?.let { gyroStats += it - fix.courseDeg }
    }

    ValueRow("Speed", lastFix?.let { "${(it.speedMps * 3.6).roundToInt()} km/h" } ?: "—")
    ValueRow(
        "GPS direction of travel",
        lastFix?.let { f -> "${fmt(f.courseDeg, 1)}°" + (f.courseAccuracyDeg?.let { " ±${fmt(it, 1)}°" } ?: "") } ?: "—",
    )
    if (problem != null) {
        Text(
            courseProblemText(problem, CarMath.MIN_TEST_SPEED_MPS) + " (Not counting.)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ValueRow(
        "Compass heading",
        when {
            compassTrue == null -> "waiting…"
            lastFix != null && problem == null -> "${fmt(compassTrue, 1)}°  (off by ${signed(wrap180(compassTrue - lastFix.courseDeg))}°)"
            else -> "${fmt(compassTrue, 1)}°"
        },
    )
    ValueRow(
        "Gyroscope heading",
        when {
            reading == null -> "starting…"
            gyroTrue == null -> if (lineUp != null) "line up again" else "tap Line up"
            lastFix != null && problem == null -> "${fmt(gyroTrue, 1)}°  (off by ${signed(wrap180(gyroTrue - lastFix.courseDeg))}°)"
            else -> "${fmt(gyroTrue, 1)}°"
        },
    )
    validLineUp?.let { ValueRow("Lined up", "${minutesSeconds(it.ageSeconds(now))} ago") }

    Spacer(Modifier.height(4.dp))
    Text("Compass error", fontWeight = FontWeight.SemiBold)
    Text(statsText(compassStats), style = MaterialTheme.typography.bodyMedium)
    Text("Gyroscope error (since last line-up)", fontWeight = FontWeight.SemiBold)
    Text(statsText(gyroStats), style = MaterialTheme.typography.bodyMedium)

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = reading != null && !lining,
            onClick = {
                lining = true
                scope.launch {
                    when (val r = performLineUp(gyroFlow) { fixes.toList() }) {
                        is LineUpResult.Done -> {
                            lineUp = r.lineUp
                            gyroStats = ErrorStats()
                            Toast.makeText(context, "Lined up", Toast.LENGTH_SHORT).show()
                        }
                        is LineUpResult.Failed -> Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                    }
                    lining = false
                }
            },
        ) { Text(if (lining) "Hold still…" else "Line up") }
        OutlinedButton(onClick = { compassStats = ErrorStats(); gyroStats = ErrorStats() }) { Text("Reset") }
        OutlinedButton(onClick = onStop) { Text("Stop") }
    }
    Text(
        "Note the gyroscope error after about 2, 5 and 10 minutes. Leaving this screen stops the test " +
            "and you'll need to line up again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun statsText(s: ErrorStats): String {
    val mean = s.meanDeg ?: return "No samples yet. Counts only above 20 km/h on straight roads."
    return "Average ${signed(mean)}°, spread ±${fmt(s.spreadDeg ?: 0.0, 1)}°, worst ${fmt(s.worstDeg, 0)}°, " +
        "now ${s.lastDeg?.let { signed(it) } ?: "—"}° (${s.count} samples)"
}
