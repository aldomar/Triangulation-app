package com.triangulation.locator.ui

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.triangulation.locator.data.Observation
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.sensors.normalize360
import com.triangulation.locator.solver.CoordinateParser
import com.triangulation.locator.solver.LatLon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Form for a bearing measured by someone else (a fire lookout on the radio, a hand compass,
 * a friend's phone). Saved into [sessionId] without a photo.
 */
@Composable
fun TypedBearingScreen(store: ObservationStore, sessionId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var coordText by rememberSaveable { mutableStateOf("") }
    /** Set when the position came from this phone's GPS; cleared when the text is edited. */
    var gpsAccuracyM by rememberSaveable { mutableStateOf<Double?>(null) }
    var bearingText by rememberSaveable { mutableStateOf("") }
    var magnetic by rememberSaveable { mutableStateOf(false) }
    var errorDeg by rememberSaveable { mutableFloatStateOf(5f) }
    var minutesAgoText by rememberSaveable { mutableStateOf("0") }
    var note by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }

    // GPS runs only after "Use my GPS position" is tapped, and stops once a fresh fix arrives.
    var gpsWanted by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberGrantedPermissions(*LOCATION_PERMISSIONS)
    val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION]
    val gps by rememberGpsState(enabled = gpsWanted && hasFineLocation)
    LaunchedEffect(gps?.location) {
        val loc = gps?.location
        if (gpsWanted && loc != null && loc.ageSeconds() <= 30) {
            coordText = "${fmt(loc.latitude, 6)}, ${fmt(loc.longitude, 6)}"
            gpsAccuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
            gpsWanted = false
        }
    }

    // Where the map picker starts: the typed point, else the session's latest bearing.
    val sessionHint by produceState<LatLon?>(null, sessionId) {
        value = store.includedObservations(sessionId).lastOrNull()?.let { LatLon(it.latitude, it.longitude) }
    }

    val position = CoordinateParser.parse(coordText)
    val bearing = bearingText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it <= 360.0 }
    val minutesAgo = minutesAgoText.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..1440 }
    val declination = position?.let { declinationAt(it.lat, it.lon) }
    val trueBearing = bearing?.let { b -> if (magnetic) declination?.let { normalize360(b + it) } else normalize360(b) }

    if (picking) {
        PointPicker(
            title = "Where it was taken from",
            initial = position ?: sessionHint,
            onPick = {
                coordText = "${fmt(it.lat, 6)}, ${fmt(it.lon, 6)}"
                gpsAccuracyM = null
                picking = false
            },
            onCancel = { picking = false },
        )
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader("Add a bearing", onDone)
            Text(
                "For a bearing someone else measured: a fire lookout on the radio, a hand compass, or another " +
                    "phone. It's used on the map like your own bearings. It has no photo, so it can't use the " +
                    "terrain estimate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- Position ----
            Text("Where it was taken from", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = coordText,
                onValueChange = { coordText = it; gpsAccuracyM = null },
                label = { Text("Latitude, longitude") },
                placeholder = { Text("49.2827, -123.1207") },
                singleLine = true,
                isError = coordText.isNotBlank() && position == null,
                supportingText = {
                    Text(
                        when {
                            coordText.isBlank() -> "Decimal degrees, or degrees and minutes like 49°16.9' N 123°07.2' W"
                            position == null -> "Can't read this position. Try 49.2827, -123.1207"
                            gpsAccuracyM != null -> "From this phone's GPS, ±${gpsAccuracyM!!.roundToInt()} m"
                            else -> "Reads as ${fmt(position.lat, 5)}, ${fmt(position.lon, 5)}"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (hasFineLocation) gpsWanted = true else permissions.request()
                }) { Text(if (gpsWanted) "Waiting for GPS…" else "Use my GPS position") }
                OutlinedButton(onClick = { picking = true }) { Text("Pick on map") }
            }

            // ---- Bearing ----
            Text("Bearing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = bearingText,
                onValueChange = { bearingText = it },
                label = { Text("Degrees, 0 to 360") },
                singleLine = true,
                isError = bearingText.isNotBlank() && bearing == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("True north", selected = !magnetic) { magnetic = false }
                ChoiceButton("Magnetic north", selected = magnetic) { magnetic = true }
            }
            Text(
                when {
                    bearing == null -> "Lookouts and maps usually give true bearings. A hand compass gives magnetic, " +
                        "unless it has been set for declination."
                    magnetic && declination == null -> "Enter the position first, so the app can work out the declination there."
                    magnetic && trueBearing != null ->
                        "Declination there is ${fmt(abs(declination!!), 1)}° ${if (declination >= 0) "E" else "W"}, " +
                            "so the true bearing is ${fmt(trueBearing, 1)}° ${cardinal(trueBearing)}."
                    trueBearing != null -> "True bearing ${fmt(trueBearing, 1)}° ${cardinal(trueBearing)}."
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- Error band ----
            Text("Error band ±${errorDeg.roundToInt()}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Slider(
                value = errorDeg,
                onValueChange = { errorDeg = it.roundToInt().toFloat() },
                valueRange = 1f..30f,
                steps = 28,
            )
            Text(
                "How far off it might be. A lookout's firefinder or a good hand compass: ±2–5°. A rough guess: ±10° or more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- When and who ----
            OutlinedTextField(
                value = minutesAgoText,
                onValueChange = { minutesAgoText = it },
                label = { Text("Taken how many minutes ago") },
                singleLine = true,
                isError = minutesAgo == null,
                supportingText = { if (minutesAgo == null) Text("A whole number from 0 to 1440") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("Lookout on Mt X, by radio") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val p = position ?: return@Button
                    val b = bearing ?: return@Button
                    val t = trueBearing ?: return@Button
                    val mins = minutesAgo ?: return@Button
                    saving = true
                    scope.launch {
                        try {
                            store.saveTyped(
                                typedObservation(p, b, t, magnetic, declination ?: 0.0, errorDeg.toDouble(), mins, note, gpsAccuracyM),
                                sessionId,
                            )
                            Toast.makeText(context, "Bearing added", Toast.LENGTH_SHORT).show()
                            onDone()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            saving = false
                            Toast.makeText(context, "Couldn't save the bearing.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = position != null && trueBearing != null && minutesAgo != null && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save bearing", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 14.dp)
    if (selected) {
        Button(onClick = onClick, contentPadding = padding) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = padding) { Text(label) }
    }
}

private fun typedObservation(
    position: LatLon,
    typedBearing: Double,
    trueBearing: Double,
    magnetic: Boolean,
    declination: Double,
    errorDeg: Double,
    minutesAgo: Int,
    note: String,
    gpsAccuracyM: Double?,
): Observation {
    val now = System.currentTimeMillis()
    return Observation(
        id = "typed_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now)),
        timeMillis = now - minutesAgo * 60_000L,
        latitude = position.lat,
        longitude = position.lon,
        altitudeM = null,
        gpsAccuracyM = gpsAccuracyM,
        // See Observation.typedIn: magnetic input keeps the typed value and the declination;
        // true input is stored like car mode (declination 0).
        magneticHeadingDeg = if (magnetic) normalize360(typedBearing) else trueBearing,
        headingSpreadDeg = 0.0,
        declinationDeg = if (magnetic) declination else 0.0,
        pitchDeg = 0.0,
        rollDeg = 0.0,
        compassAccuracy = -1,
        tapX = 0.5,
        tapY = 0.5,
        focalLengthFraction = null,
        tapOffsetDeg = 0.0,
        bearingDeg = trueBearing,
        errorDeg = errorDeg,
        photoFileName = "",
        photoWidth = 0,
        photoHeight = 0,
        typedIn = true,
        note = note.trim().ifEmpty { null },
    )
}
