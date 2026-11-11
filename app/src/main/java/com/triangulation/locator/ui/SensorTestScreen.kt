package com.triangulation.locator.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triangulation.locator.sensors.GpsState
import com.triangulation.locator.sensors.OrientationState
import com.triangulation.locator.sensors.gpsUpdates
import com.triangulation.locator.sensors.normalize360
import com.triangulation.locator.sensors.orientationUpdates
import com.triangulation.locator.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Phase 1: live readout of GPS, true heading, tilt and compass calibration. */
@SuppressLint("MissingPermission") // GPS is only started after hasFineLocation is true.
@Composable
fun SensorTestScreen() {
    val context = LocalContext.current
    val locationManager = remember { context.getSystemService(LocationManager::class.java) }
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }

    var hasFineLocation by remember { mutableStateOf(context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var hasCoarseLocation by remember { mutableStateOf(context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) }
    // Re-check when returning from the system Settings app.
    LifecycleResumeEffect(Unit) {
        hasFineLocation = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        hasCoarseLocation = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasFineLocation = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        hasCoarseLocation = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // Sensors run only while this screen is visible (collectAsStateWithLifecycle stops them
    // when the app goes to the background), which keeps battery use low.
    val orientationFlow = remember { sensorManager.orientationUpdates() }
    val orientation by orientationFlow.collectAsStateWithLifecycle(OrientationState.Waiting)
    val gpsFlow = remember(hasFineLocation) {
        if (hasFineLocation) locationManager.gpsUpdates() else flowOf(null)
    }
    val gps by gpsFlow.collectAsStateWithLifecycle(null)

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
            Text("Sensor check", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Make sure GPS and the compass are working before taking bearings. Best results outdoors, away from cars and metal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!hasFineLocation) {
                PermissionCard(
                    approximateOnly = hasCoarseLocation,
                    onRequest = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    },
                    onOpenSettings = { context.openAppSettings() },
                )
            } else {
                GpsCard(gps, onOpenLocationSettings = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                })
            }

            HeadingCard(orientation, declination)
            CompassAccuracyCard(orientation)
            TiltCard(orientation)

            Text(
                "Nothing leaves this phone. The app has no internet permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Cards ----------

@Composable
private fun PermissionCard(approximateOnly: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
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
            val accuracy = if (location.hasAccuracy()) location.accuracy else null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Accuracy", Modifier.weight(1f))
                if (accuracy != null) {
                    StatusBadge("±${fmt(accuracy.toDouble(), 0)} m", gpsAccuracyColor(accuracy))
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
                    (ageSeconds?.let { " (${it} s ago)" } ?: ""),
            )
            if (ageSeconds != null && ageSeconds > 10) {
                Warning("This position is old. Waiting for a fresh fix.")
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
                    Warning("Showing magnetic heading. True north correction starts once GPS has a fix.")
                }
                if (abs(orientation.pitchDeg) > 60) {
                    Warning("Hold the phone upright, with the back camera pointing at the object. Heading is unreliable when the phone is flat.")
                }
            }
        }
    }
}

@Composable
private fun CompassAccuracyCard(orientation: OrientationState) {
    if (orientation !is OrientationState.Reading) return
    val (label, color) = when (orientation.accuracy) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High" to StatusColors.Good
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium" to StatusColors.Fair
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low" to StatusColors.Poor
        else -> "Unreliable" to StatusColors.Bad
    }
    InfoCard("Compass calibration") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Status", Modifier.weight(1f))
            StatusBadge(label, color)
        }
        if (orientation.accuracy != SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            Spacer(Modifier.height(8.dp))
            Text(
                "To calibrate: move away from cars, metal and power lines, then slowly wave the phone " +
                    "in a figure-8 shape 3–4 times, turning it as you go.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

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

// ---------- Small building blocks ----------

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun Warning(text: String) {
    Text(text, color = StatusColors.Poor, style = MaterialTheme.typography.bodyMedium)
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

/** Current elapsed-realtime clock, ticking once a second, for "x s ago" labels. */
@Composable
private fun rememberNowTicker(): Long {
    val now by produceState(SystemClock.elapsedRealtimeNanos()) {
        while (true) {
            delay(1_000)
            value = SystemClock.elapsedRealtimeNanos()
        }
    }
    return now
}

// ---------- Helpers ----------

private fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", packageName, null))
    )
}

/** Magnetic declination at this position: add it to a magnetic heading to get true heading. */
private fun Location.declinationDeg(): Double =
    GeomagneticField(
        latitude.toFloat(),
        longitude.toFloat(),
        if (hasAltitude()) altitude.toFloat() else 0f,
        System.currentTimeMillis(),
    ).declination.toDouble()

private fun Location.ageSeconds(nowElapsedNanos: Long): Long =
    ((nowElapsedNanos - elapsedRealtimeNanos) / 1_000_000_000L).coerceAtLeast(0)

private fun gpsAccuracyColor(meters: Float): Color = when {
    meters <= 10f -> StatusColors.Good
    meters <= 25f -> StatusColors.Fair
    meters <= 50f -> StatusColors.Poor
    else -> StatusColors.Bad
}

private val CARDINALS = arrayOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
)

private fun cardinal(deg: Double): String = CARDINALS[((normalize360(deg) + 11.25) / 22.5).toInt() % 16]

private fun fmt(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

private fun signed(value: Double): String = String.format(Locale.US, "%+.1f", value)
