package com.triangulation.locator.ui

import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.triangulation.locator.data.CompassCheck
import com.triangulation.locator.data.Observation
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.sensors.MagneticCheck
import com.triangulation.locator.solver.CoordinateParser
import com.triangulation.locator.solver.LandmarkCheck
import com.triangulation.locator.solver.LatLon
import kotlin.math.abs

private enum class CheckStep { Setup, Aim, Result }

/**
 * Compass check against a landmark whose position is known: choose the landmark, take a bearing
 * of it with the normal capture screen, then see how far off the compass is. Each check is saved
 * to the list on the Sensors screen. It never changes any bearing.
 */
@Composable
fun LandmarkCheckScreen(store: ObservationStore, onDone: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(CheckStep.Setup) }
    var name by rememberSaveable { mutableStateOf("") }
    var coordText by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    // The measured bearing isn't kept across Android closing the app; then it's back to Setup.
    var measured by remember { mutableStateOf<Observation?>(null) }

    val landmark = CoordinateParser.parse(coordText)
    val target = landmark?.let { CheckTarget(name.trim().ifEmpty { null }, it) }

    BackHandler(enabled = step != CheckStep.Setup) { step = CheckStep.Setup }

    val m = measured
    when {
        picking -> PointPicker(
            title = "Where the landmark is",
            initial = landmark,
            onPick = {
                coordText = "${fmt(it.lat, 6)}, ${fmt(it.lon, 6)}"
                picking = false
            },
            onCancel = { picking = false },
        )
        step == CheckStep.Aim && target != null -> CaptureScreen(
            store = store,
            onOpenSensors = {},
            onOpenHistory = {},
            onOpenSession = {},
            check = target,
            onCheckMeasured = {
                measured = it
                step = CheckStep.Result
            },
            onCancelCheck = { step = CheckStep.Setup },
        )
        step == CheckStep.Result && target != null && m != null -> CheckResult(
            store = store,
            target = target,
            obs = m,
            onAgain = { step = CheckStep.Aim },
            onDone = onDone,
        )
        else -> CheckSetup(
            name = name,
            onName = { name = it },
            coordText = coordText,
            onCoordText = { coordText = it },
            landmark = landmark,
            onPick = { picking = true },
            onStart = { step = CheckStep.Aim },
            onBack = onDone,
        )
    }
}

@Composable
private fun CheckSetup(
    name: String,
    onName: (String) -> Unit,
    coordText: String,
    onCoordText: (String) -> Unit,
    landmark: LatLon?,
    onPick: () -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader("Check the compass", onBack)
            Text(
                "Take a bearing of something whose exact position you know, such as a radio mast, a summit or a " +
                    "lookout tower, ideally more than 1 km away. The app compares your bearing with the true " +
                    "direction to it, and keeps the result. It doesn't change any of your bearings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("Landmark name (optional)") },
                placeholder = { Text("Radio mast on Mt X") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = coordText,
                onValueChange = onCoordText,
                label = { Text("Landmark position: latitude, longitude") },
                placeholder = { Text("49.2827, -123.1207") },
                singleLine = true,
                isError = coordText.isNotBlank() && landmark == null,
                supportingText = {
                    Text(
                        when {
                            coordText.isBlank() -> "From a map or a summit list. Or pick it on the map below."
                            landmark == null -> "Can't read this position. Try 49.2827, -123.1207"
                            else -> "Reads as ${fmt(landmark.lat, 5)}, ${fmt(landmark.lon, 5)}"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = onPick) { Text("Pick on map") }
            Text(
                "The position must be accurate: a landmark placed 50 m off, 2 km away, moves the true direction by " +
                    "about 1.4°. Zoom right in when picking on the map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStart,
                enabled = landmark != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Aim at it", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun CheckResult(
    store: ObservationStore,
    target: CheckTarget,
    obs: Observation,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val observer = LatLon(obs.latitude, obs.longitude)
    val c = remember(obs) { LandmarkCheck.compare(observer, target.point, obs.bearingDeg, obs.gpsAccuracyM) }
    var saved by remember(obs) { mutableStateOf<Boolean?>(null) }

    // Record the check once.
    LaunchedEffect(obs) {
        saved = try {
            store.saveCompassCheck(
                CompassCheck(
                    timeMillis = obs.timeMillis,
                    latitude = obs.latitude,
                    longitude = obs.longitude,
                    gpsAccuracyM = obs.gpsAccuracyM,
                    landmarkName = target.name,
                    landmarkLatitude = target.point.lat,
                    landmarkLongitude = target.point.lon,
                    measuredBearingDeg = obs.bearingDeg,
                    expectedBearingDeg = c.expectedBearingDeg,
                    differenceDeg = c.differenceDeg,
                    distanceM = c.distanceM,
                    compassAccuracy = obs.compassAccuracy,
                    magneticFieldRatio = obs.magneticFieldRatio,
                    headingSpreadDeg = obs.headingSpreadDeg,
                    tapX = obs.tapX,
                    tapY = obs.tapY,
                )
            )
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ScreenHeader("Compass check", onDone)
            Text(
                "Your compass ${LandmarkCheck.describe(c.differenceDeg)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            ValueRow("Your bearing", "${fmt(obs.bearingDeg, 1)}° ${cardinal(obs.bearingDeg)}")
            ValueRow("True bearing to ${target.label}", "${fmt(c.expectedBearingDeg, 1)}° ${cardinal(c.expectedBearingDeg)}")
            ValueRow("Difference", "${signed(c.differenceDeg)}°")
            ValueRow("Distance", distanceText(c.distanceM))
            c.gpsExplainsDeg?.let { ValueRow("GPS error alone could explain", "±${fmt(it, 1)}°") }
            ValueRow("Compass calibration", compassAccuracyLabel(obs.compassAccuracy).first)
            ValueRow("Tapped", if (abs(obs.tapX - 0.5) < 0.05 && abs(obs.tapY - 0.5) < 0.05) "near the photo centre" else "away from the centre")

            if (c.tooClose) {
                WarningText(
                    "The landmark is under 1 km away, so small errors in your GPS position or the landmark's position " +
                        "change the true direction a lot. Prefer one further away."
                )
            }
            if (c.implausible) {
                WarningText(
                    "That's a very large difference. Check that you tapped the right landmark and that its position " +
                        "is correct before trusting this result."
                )
            }
            obs.magneticFieldRatio?.takeIf { MagneticCheck.isDisturbed(it) }?.let {
                WarningText("There was magnetic interference (field ${MagneticCheck.describe(it)}), so this check mostly measures that.")
            }
            if (obs.compassAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
                obs.compassAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
            ) {
                WarningText("Compass calibration was low. Calibrate (figure-8) and check again.")
            }
            if (obs.headingSpreadDeg > 3) WarningText("The phone moved while measuring. Hold still and check again.")

            Text(
                when (saved) {
                    null -> "Saving…"
                    true -> "Saved to the list of checks on the Sensors screen. Your bearings haven't been changed."
                    false -> "Couldn't save this check."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).height(52.dp)) { Text("Check again") }
                Button(onClick = onDone, modifier = Modifier.weight(1f).height(52.dp)) { Text("Done") }
            }
        }
    }
}
