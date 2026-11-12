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
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

// ---------- Sensor streams ----------
// Collected with collectAsStateWithLifecycle, so sensors stop when the app is in the background.

@Composable
fun rememberOrientationFlow(): Flow<OrientationState> {
    val context = LocalContext.current
    return remember { context.getSystemService(SensorManager::class.java).orientationUpdates() }
}

@Composable
fun rememberOrientationState(flow: Flow<OrientationState> = rememberOrientationFlow()): State<OrientationState> =
    flow.collectAsStateWithLifecycle(OrientationState.Waiting)

/** GPS readings, or null while [enabled] is false (no permission yet). */
@SuppressLint("MissingPermission") // Callers pass enabled = true only after the permission is granted.
@Composable
fun rememberGpsState(enabled: Boolean): State<GpsState?> {
    val context = LocalContext.current
    val flow = remember(enabled) {
        if (enabled) context.getSystemService(LocationManager::class.java).gpsUpdates() else flowOf(null)
    }
    return flow.collectAsStateWithLifecycle(null)
}

// ---------- Permissions ----------

class GrantedPermissions(private val granted: Map<String, Boolean>, val request: () -> Unit) {
    operator fun get(permission: String): Boolean = granted[permission] == true
    val all: Boolean get() = granted.values.all { it }
}

/** Tracks whether [permissions] are granted, re-checking when the user returns from Settings. */
@Composable
fun rememberGrantedPermissions(vararg permissions: String): GrantedPermissions {
    val context = LocalContext.current
    fun check() = permissions.associateWith { context.hasPermission(it) }
    var granted by remember { mutableStateOf(check()) }
    LifecycleResumeEffect(Unit) {
        granted = check()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = check()
    }
    return GrantedPermissions(granted) { launcher.launch(arrayOf(*permissions)) }
}

val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

fun Context.hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.fromParts("package", packageName, null))
    )
}

fun Context.openLocationSettings() {
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

// ---------- Building blocks ----------

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("← Back") }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun WarningText(text: String) {
    Text(text, color = StatusColors.Poor, style = MaterialTheme.typography.bodyMedium)
}

/** Current elapsed-realtime clock, ticking once a second, for "x s ago" labels. */
@Composable
fun rememberNowTicker(): Long {
    val now by produceState(SystemClock.elapsedRealtimeNanos()) {
        while (true) {
            delay(1_000)
            value = SystemClock.elapsedRealtimeNanos()
        }
    }
    return now
}

// ---------- Formatting and small calculations ----------

/** Magnetic declination at this position: add it to a magnetic heading to get true heading. */
fun Location.declinationDeg(): Double =
    GeomagneticField(
        latitude.toFloat(),
        longitude.toFloat(),
        if (hasAltitude()) altitude.toFloat() else 0f,
        System.currentTimeMillis(),
    ).declination.toDouble()

fun Location.ageSeconds(nowElapsedNanos: Long = SystemClock.elapsedRealtimeNanos()): Long =
    ((nowElapsedNanos - elapsedRealtimeNanos) / 1_000_000_000L).coerceAtLeast(0)

fun gpsAccuracyColor(meters: Double): Color = when {
    meters <= 10 -> StatusColors.Good
    meters <= 25 -> StatusColors.Fair
    meters <= 50 -> StatusColors.Poor
    else -> StatusColors.Bad
}

fun compassAccuracyLabel(accuracy: Int): Pair<String, Color> = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High" to StatusColors.Good
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium" to StatusColors.Fair
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low" to StatusColors.Poor
    else -> "Unreliable" to StatusColors.Bad
}

private val CARDINALS = arrayOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
)

fun cardinal(deg: Double): String = CARDINALS[((normalize360(deg) + 11.25) / 22.5).toInt() % 16]

fun fmt(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

fun signed(value: Double, decimals: Int = 1): String = String.format(Locale.US, "%+.${decimals}f", value)
