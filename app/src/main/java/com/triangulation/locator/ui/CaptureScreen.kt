package com.triangulation.locator.ui

import android.Manifest
import android.graphics.Bitmap
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.triangulation.locator.camera.CameraOptics
import com.triangulation.locator.camera.cameraProvider
import com.triangulation.locator.camera.takeUprightPhoto
import com.triangulation.locator.data.BearingMath
import com.triangulation.locator.terrain.TerrainMath
import com.triangulation.locator.data.Observation
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.data.Session
import com.triangulation.locator.data.Settings
import com.triangulation.locator.car.CarMath
import com.triangulation.locator.car.CourseFix
import com.triangulation.locator.car.LineUp
import com.triangulation.locator.sensors.GyroState
import com.triangulation.locator.sensors.hasGyroHeading
import com.triangulation.locator.solver.LatLon
import android.os.SystemClock
import androidx.compose.material3.Switch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.text.style.TextOverflow
import com.triangulation.locator.sensors.MagneticCheck
import com.triangulation.locator.sensors.OrientationState
import com.triangulation.locator.sensors.circularMeanDeg
import com.triangulation.locator.sensors.circularSpreadDeg
import com.triangulation.locator.sensors.normalize360
import com.triangulation.locator.ui.theme.StatusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "CaptureScreen"

/** How long the heading is averaged over when the shutter is pressed. */
private const val STEADY_MS = 1_000L

/** Everything recorded at the moment of the photo, before the user taps the target. */
private class PendingCapture(
    val photo: Bitmap,
    val timeMillis: Long,
    val location: Location,
    val locationAgeSeconds: Long,
    val magneticHeadingDeg: Double,
    val headingSpreadDeg: Double,
    val declinationDeg: Double,
    val pitchDeg: Double,
    val rollDeg: Double,
    val compassAccuracy: Int,
    /** Null if the phone didn't report its lens details. */
    val focalFraction: Double?,
    /** Measured ÷ expected magnetic field strength, averaged over the second (null in car mode or if unknown). */
    val magneticFieldRatio: Double? = null,
    /** Set in car mode. Then [magneticHeadingDeg] is the TRUE heading and [declinationDeg] is 0. */
    val car: CarCapture? = null,
)

/** Car-mode details of a capture. */
private class CarCapture(
    /** Where the car was at the moment of the photo (moved on from the last GPS fix). */
    val position: LatLon,
    val speedMps: Double?,
    val lineUpAgeSeconds: Double,
    /** How far the GPS fix was moved forward in time to the photo, seconds. */
    val extrapolatedSeconds: Double,
    val defaultErrorDeg: Double,
)

private sealed interface Step {
    data object Aiming : Step
    data object Steadying : Step
    class Review(val pending: PendingCapture) : Step
}

/** A landmark whose position is known, aimed at in compass-check mode. */
class CheckTarget(val name: String?, val point: LatLon) {
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: "the landmark"
}

/**
 * Main screen: aim at the target, take a bearing, tap the target on the photo, save.
 *
 * With [check] set it runs as a compass check instead: no session, no car mode, and the
 * finished bearing goes to [onCheckMeasured] instead of being saved (the photo is dropped).
 */
@Composable
fun CaptureScreen(
    store: ObservationStore,
    onOpenSensors: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSession: (Long) -> Unit,
    check: CheckTarget? = null,
    onCheckMeasured: (Observation) -> Unit = {},
    onCancelCheck: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = rememberGrantedPermissions(Manifest.permission.CAMERA, *LOCATION_PERMISSIONS)
    val hasCamera = permissions[Manifest.permission.CAMERA]
    val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION]

    // Car mode uses the gyroscope instead of the compass; only one of the two runs at a time.
    val settings = remember { Settings(context.applicationContext) }
    val hasGyro = remember { context.getSystemService(SensorManager::class.java).hasGyroHeading() }
    // A compass check must use the compass, so car mode is off for it.
    val carOn = CarSession.enabled && hasGyro && check == null
    var askCarSafety by remember { mutableStateOf(false) }
    var liningUp by remember { mutableStateOf(false) }

    val orientationFlow = rememberOrientationFlow()
    val activeOrientationFlow = remember(carOn) { if (carOn) flowOf(OrientationState.Waiting) else orientationFlow }
    val orientation by rememberOrientationState(activeOrientationFlow)
    val gyroFlow = rememberSharedGyroFlow()
    val activeGyroFlow = remember(carOn) { if (carOn) gyroFlow else flowOf(GyroState.Waiting) }
    val gyro by activeGyroFlow.collectAsStateWithLifecycle(GyroState.Waiting)
    val gyroReading = gyro as? GyroState.Reading
    // A line-up only counts for the gyroscope run it was made in.
    val lineUp = CarSession.lineUp?.takeIf { gyroReading != null && it.runId == gyroReading.runId }

    val gpsState = rememberGpsState(enabled = hasFineLocation)
    val gps = gpsState.value
    val fixes = rememberCourseFixes(gps?.location)

    fun setCarMode(on: Boolean) {
        if (on && !settings.carSafetyAcknowledged) {
            askCarSafety = true
            return
        }
        CarSession.enabled = on
        if (!on) CarSession.lineUp = null
    }
    if (askCarSafety) {
        CarSafetyDialog(
            onAccept = {
                askCarSafety = false
                settings.carSafetyAcknowledged = true
                setCarMode(true)
            },
            onCancel = { askCarSafety = false },
        )
    }

    var step by remember { mutableStateOf<Step>(Step.Aiming) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var optics by remember { mutableStateOf<CameraOptics?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // The session new bearings go into. Null until the first bearing starts one.
    var activeSession by remember { mutableStateOf<Session?>(null) }
    var namingSession by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { activeSession = store.activeSession() }

    if (namingSession) {
        SessionNameDialog(
            title = "New session",
            initial = ObservationStore.defaultSessionName(),
            confirmLabel = "Start",
            onConfirm = { name ->
                namingSession = false
                scope.launch { activeSession = store.startSession(name) }
            },
            onDismiss = { namingSession = false },
        )
    }

    val current = step
    if (current is Step.Review) {
        BackHandler { step = Step.Aiming }
        ReviewScreen(
            pending = current.pending,
            onRetake = { step = Step.Aiming },
            saveLabel = if (check != null) "Compare" else "Save",
            onSave = { observation ->
                if (check != null) {
                    step = Step.Aiming
                    onCheckMeasured(observation)
                    return@ReviewScreen
                }
                scope.launch {
                    try {
                        val session = store.save(observation, current.pending.photo)
                        activeSession = session
                        Toast.makeText(
                            context,
                            "Saved to \"${session.name}\". Take the next one from a different place.",
                            Toast.LENGTH_LONG,
                        ).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Save failed", e)
                        Toast.makeText(context, "Couldn't save. Is the phone's storage full?", Toast.LENGTH_LONG).show()
                    }
                    step = Step.Aiming
                }
            },
        )
    } else {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (check != null) "Compass check" else "Take a bearing",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (check != null) {
                        TextButton(onClick = onCancelCheck) { Text("Cancel") }
                    } else {
                        TextButton(onClick = onOpenSensors) { Text("Sensors") }
                        TextButton(onClick = onOpenHistory) { Text("History") }
                    }
                }
                if (check != null) {
                    Text(
                        "Aim at ${check.label} (${fmt(check.point.lat, 5)}, ${fmt(check.point.lon, 5)}). " +
                            "Nothing is saved to your sessions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else SessionBar(
                    session = activeSession,
                    onOpen = { activeSession?.let { onOpenSession(it.id) } },
                    onNew = { namingSession = true },
                )
                if (check == null) CarModeBar(
                    available = hasGyro,
                    on = carOn,
                    lineUp = lineUp,
                    lineUpLost = CarSession.lineUp != null && lineUp == null,
                    liningUp = liningUp,
                    onToggle = ::setCarMode,
                    onLineUp = {
                        liningUp = true
                        scope.launch {
                            when (val r = performLineUp(gyroFlow) { fixes.toList() }) {
                                is LineUpResult.Done -> {
                                    CarSession.lineUp = r.lineUp
                                    Toast.makeText(context, "Lined up", Toast.LENGTH_SHORT).show()
                                }
                                is LineUpResult.Failed -> Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                            }
                            liningUp = false
                        }
                    },
                )

                if (!hasCamera || !hasFineLocation) {
                    PermissionsNeeded(
                        approximateOnly = !hasFineLocation && permissions[Manifest.permission.ACCESS_COARSE_LOCATION],
                        onRequest = permissions.request,
                        onOpenSettings = { context.openAppSettings() },
                    )
                } else {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CameraPreview(
                            onBound = { capture, cameraOptics ->
                                imageCapture = capture
                                optics = cameraOptics
                                cameraError = null
                            },
                            onUnbound = { imageCapture = null },
                            onError = { cameraError = it },
                        )
                        Crosshair()
                        cameraError?.let {
                            Text(it, color = Color.White, modifier = Modifier.padding(24.dp))
                        }
                    }

                    if (carOn) {
                        CarReadout(gyroReading, lineUp, fixes.lastOrNull(), gps?.location, gps?.providerEnabled != false)
                    } else {
                        LiveReadout(orientation, gps?.location, gps?.providerEnabled != false)
                    }

                    val blocker = when {
                        imageCapture == null -> "Starting camera…"
                        gps?.providerEnabled == false -> "Turn on location (GPS) to take a bearing."
                        gps?.location == null -> "Waiting for a GPS fix… Go outside with a clear view of the sky."
                        carOn && gyro == GyroState.Unavailable -> "This phone has no gyroscope, so car mode can't be used."
                        carOn && gyroReading == null -> "Starting gyroscope…"
                        carOn && lineUp == null ->
                            "Line up first: on a straight road, put the crosshair on a distant point straight ahead and tap Line up."
                        carOn -> null
                        orientation == OrientationState.Unavailable -> "This phone has no compass, so it can't take bearings."
                        orientation !is OrientationState.Reading -> "Starting compass…"
                        else -> null
                    }
                    if (step == Step.Steadying) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Hold still…", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Text(
                            blocker ?: if (check != null) {
                                "Put the crosshair on ${check.label}, press the button, and hold still for a second."
                            } else {
                                "Put the crosshair on the object, press the button, and hold still for a second."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (blocker != null) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                imageCapture?.let { capture ->
                                    step = Step.Steadying
                                    scope.launch {
                                        val carLineUp = lineUp
                                        val pending = if (carOn && carLineUp != null) {
                                            takeCarBearing(capture, gyroFlow, carLineUp, { gpsState.value?.location }, optics)
                                        } else {
                                            takeBearing(capture, orientationFlow, { gpsState.value?.location }, optics)
                                        }
                                        if (pending == null) {
                                            Toast.makeText(context, "Couldn't take the bearing. Please try again.", Toast.LENGTH_LONG).show()
                                        }
                                        step = if (pending != null) Step.Review(pending) else Step.Aiming
                                    }
                                }
                            },
                            enabled = blocker == null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text("Take bearing", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Averages the compass for [STEADY_MS] and takes the photo in the middle of that window.
 * Returns null if the photo, compass or GPS failed.
 */
private suspend fun takeBearing(
    capture: ImageCapture,
    orientationFlow: Flow<OrientationState>,
    currentLocation: () -> Location?,
    optics: CameraOptics?,
): PendingCapture? {
    try {
        val (photo, samples) = photoWithSamples(capture, orientationFlow.filterIsInstance<OrientationState.Reading>())
        val location = currentLocation()
        if (samples.isEmpty() || location == null) return null
        val headings = samples.map { it.rawMagneticHeadingDeg }
        val fields = samples.mapNotNull { it.rawFieldStrengthUt }
        val fieldRatio = MagneticCheck.ratio(fields.takeIf { it.isNotEmpty() }?.average(), location.expectedFieldUt())
        return PendingCapture(
            photo = photo,
            timeMillis = System.currentTimeMillis(),
            location = location,
            locationAgeSeconds = location.ageSeconds(),
            magneticHeadingDeg = normalize360(circularMeanDeg(headings)),
            headingSpreadDeg = circularSpreadDeg(headings),
            declinationDeg = location.declinationDeg(),
            pitchDeg = circularMeanDeg(samples.map { it.rawPitchDeg }),
            rollDeg = circularMeanDeg(samples.map { it.rawRollDeg }),
            compassAccuracy = samples.last().accuracy,
            focalFraction = optics?.focalFraction(photo.width, photo.height),
            magneticFieldRatio = fieldRatio,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Capture failed", e)
        return null
    }
}

/** Collects [readings] for [STEADY_MS] and takes the photo halfway through. */
private suspend fun <T> photoWithSamples(capture: ImageCapture, readings: Flow<T>): Pair<Bitmap, List<T>> =
    coroutineScope {
        val samples = mutableListOf<T>()
        val sampling = async {
            withTimeoutOrNull(STEADY_MS) { readings.collect { samples += it } }
        }
        delay(STEADY_MS / 2)
        val photo = capture.takeUprightPhoto()
        sampling.await()
        photo to samples.toList()
    }

/**
 * Car mode: like [takeBearing], but the direction comes from the gyroscope lined up with the
 * GPS direction of travel, and the position is moved on to the moment of the photo.
 */
private suspend fun takeCarBearing(
    capture: ImageCapture,
    gyroFlow: Flow<GyroState>,
    lineUp: LineUp,
    currentLocation: () -> Location?,
    optics: CameraOptics?,
): PendingCapture? {
    try {
        val startNanos = SystemClock.elapsedRealtimeNanos()
        val (photo, readings) = photoWithSamples(capture, gyroFlow.filterIsInstance<GyroState.Reading>())
        val photoNanos = startNanos + STEADY_MS * 1_000_000L / 2
        val samples = readings.filter { it.runId == lineUp.runId }
        val location = currentLocation()
        if (samples.isEmpty() || location == null) return null
        val headings = samples.map { CarMath.trueHeading(it.rawRelativeHeadingDeg, lineUp) }

        val fixPosition = LatLon(location.latitude, location.longitude)
        val secondsAfterFix = (photoNanos - location.elapsedRealtimeNanos) / 1e9
        val position = if (location.hasSpeed() && location.hasBearing()) {
            CarMath.extrapolate(fixPosition, location.speed.toDouble(), location.bearing.toDouble(), secondsAfterFix)
        } else {
            fixPosition
        }
        val lineUpAge = lineUp.ageSeconds(photoNanos)
        return PendingCapture(
            photo = photo,
            timeMillis = System.currentTimeMillis(),
            location = location,
            locationAgeSeconds = location.ageSeconds(),
            magneticHeadingDeg = normalize360(circularMeanDeg(headings)), // true heading in car mode
            headingSpreadDeg = circularSpreadDeg(headings),
            declinationDeg = 0.0,
            pitchDeg = circularMeanDeg(samples.map { it.rawPitchDeg }),
            rollDeg = circularMeanDeg(samples.map { it.rawRollDeg }),
            compassAccuracy = -1,
            focalFraction = optics?.focalFraction(photo.width, photo.height),
            car = CarCapture(
                position = position,
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
                lineUpAgeSeconds = lineUpAge,
                extrapolatedSeconds = secondsAfterFix,
                defaultErrorDeg = CarMath.defaultErrorDeg(lineUpAge, lineUp.courseAccuracyDeg),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Car capture failed", e)
        return null
    }
}

/** Car mode switch, passenger warning, and line-up status. */
@Composable
private fun CarModeBar(
    available: Boolean,
    on: Boolean,
    lineUp: LineUp?,
    lineUpLost: Boolean,
    liningUp: Boolean,
    onToggle: (Boolean) -> Unit,
    onLineUp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Car mode", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Switch(checked = on, onCheckedChange = onToggle, enabled = available)
            Spacer(Modifier.width(8.dp))
            if (!available) {
                Text(
                    "Needs a gyroscope",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (on) {
                val now = rememberNowTicker()
                Text(
                    when {
                        lineUp != null -> "Lined up ${minutesSeconds(lineUp.ageSeconds(now))} ago"
                        lineUpLost -> "Line up again"
                        else -> "Not lined up"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lineUp != null && lineUp.ageSeconds(now) <= CarMath.LINE_UP_STALE_S) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        StatusColors.Poor
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onLineUp, enabled = !liningUp) { Text(if (liningUp) "Hold still…" else "Line up") }
            }
        }
        if (on) WarningText("Passenger only. Never use the app while driving.")
    }
}

/** Car mode's version of the live strip: gyroscope heading, speed and GPS. */
@Composable
private fun CarReadout(gyro: GyroState.Reading?, lineUp: LineUp?, fix: CourseFix?, location: Location?, gpsOn: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (gyro != null && lineUp != null) {
                val heading = CarMath.trueHeading(gyro.relativeHeadingDeg, lineUp)
                Text(
                    "${fmt(heading, 0)}° ${cardinal(heading)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("true", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            fix?.let { StatusBadge("${(it.speedMps * 3.6).roundToInt()} km/h", StatusColors.Good) }
            val accuracy = location?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble()
            when {
                !gpsOn -> StatusBadge("GPS off", StatusColors.Bad)
                accuracy != null -> StatusBadge("GPS ±${fmt(accuracy, 0)} m", gpsAccuracyColor(accuracy))
                else -> StatusBadge("GPS…", StatusColors.Bad)
            }
        }
        if (gyro != null && abs(gyro.rollDeg) > 5) {
            WarningText("Phone is tilted sideways ${fmt(abs(gyro.rollDeg), 0)}°. Hold it level.")
        }
    }
}

/** Which session new bearings go into. Tap the name to open it. */
@Composable
private fun SessionBar(session: Session?, onOpen: () -> Unit, onNew: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Session:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (session != null) {
            TextButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                Text(session.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text(
                " starts with your first bearing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(onClick = onNew) { Text("New session") }
    }
}

@Composable
private fun PermissionsNeeded(approximateOnly: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    InfoCard("Permissions needed") {
        Text(
            "To take a bearing the app needs the camera and your precise location. " +
                "Photos and positions stay on this phone and are never sent anywhere."
        )
        if (approximateOnly) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Location is set to \"approximate\". Open settings, tap Permissions → Location, " +
                    "and turn on \"Use precise location\"."
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequest) { Text("Allow") }
            OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

/**
 * Live camera view. The camera runs only while this screen is visible: it is tied to the
 * screen's lifecycle and released as soon as the user leaves.
 */
@Composable
private fun CameraPreview(
    onBound: (ImageCapture, CameraOptics?) -> Unit,
    onUnbound: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // FIT_CENTER shows the whole frame, exactly what the photo will contain.
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(lifecycleOwner) {
        try {
            val cameraProvider = context.cameraProvider().also { provider = it }
            // 4:3 matches the camera sensor, so no part of the sensor is cropped away.
            val fourByThree = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            val preview = Preview.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(fourByThree).build())
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            // Ask for about 1920x1440. Bigger photos wouldn't improve the bearing.
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(fourByThree)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1440),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            )
                        )
                        .build()
                )
                .build()
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture,
            )
            onBound(capture, CameraOptics.from(camera.cameraInfo))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Camera start failed", e)
            onError("Couldn't start the back camera. Close other camera apps and try again.")
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            provider?.unbindAll()
            onUnbound()
        }
    }
}

@Composable
private fun Crosshair() {
    Canvas(Modifier.size(64.dp)) {
        val c = center
        val r = size.minDimension / 2
        val shadow = Color.Black.copy(alpha = 0.5f)
        for ((color, width) in listOf(shadow to 5.dp.toPx(), Color.White to 2.dp.toPx())) {
            drawLine(color, Offset(c.x - r, c.y), Offset(c.x - 8, c.y), width)
            drawLine(color, Offset(c.x + 8, c.y), Offset(c.x + r, c.y), width)
            drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y - 8), width)
            drawLine(color, Offset(c.x, c.y + 8), Offset(c.x, c.y + r), width)
        }
    }
}

/** Compact strip of the numbers that matter while aiming. */
@Composable
private fun LiveReadout(orientation: OrientationState, location: Location?, gpsOn: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (orientation is OrientationState.Reading) {
                val declination = remember(location) { location?.declinationDeg() }
                val heading = normalize360(orientation.magneticHeadingDeg + (declination ?: 0.0))
                Text(
                    "${fmt(heading, 0)}° ${cardinal(heading)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (declination != null) "true" else "magnetic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                val (label, color) = compassAccuracyLabel(orientation.accuracy)
                StatusBadge("Compass: $label", color)
            } else {
                Spacer(Modifier.weight(1f))
            }
            val accuracy = location?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble()
            when {
                !gpsOn -> StatusBadge("GPS off", StatusColors.Bad)
                accuracy != null -> StatusBadge("GPS ±${fmt(accuracy, 0)} m", gpsAccuracyColor(accuracy))
                else -> StatusBadge("GPS…", StatusColors.Bad)
            }
        }
        if (orientation is OrientationState.Reading) {
            val expectedField = remember(location) { location?.expectedFieldUt() }
            val fieldRatio = MagneticCheck.ratio(orientation.fieldStrengthUt, expectedField)
            when {
                orientation.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
                    orientation.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ->
                    WarningText("Compass needs calibrating. Wave the phone in a figure-8 a few times.")
                fieldRatio != null && MagneticCheck.isDisturbed(fieldRatio) ->
                    WarningText(
                        "Magnetic interference: the field is ${MagneticCheck.describe(fieldRatio)}. " +
                            "Move away from cars, metal fences and power lines."
                    )
                abs(orientation.rollDeg) > 5 ->
                    WarningText("Phone is tilted sideways ${fmt(abs(orientation.rollDeg), 0)}°. Hold it level.")
            }
        }
    }
}

// ---------- Review: tap the target, check the numbers, save ----------

@Composable
private fun ReviewScreen(
    pending: PendingCapture,
    onRetake: () -> Unit,
    saveLabel: String,
    onSave: (Observation) -> Unit,
) {
    val photo = pending.photo
    val imageBitmap = remember(photo) { photo.asImageBitmap() }
    var tapX by remember { mutableFloatStateOf(0.5f) }
    var tapY by remember { mutableFloatStateOf(0.5f) }
    var errorDeg by remember {
        mutableFloatStateOf(
            (pending.car?.defaultErrorDeg ?: BearingMath.defaultErrorDeg(pending.compassAccuracy, pending.magneticFieldRatio)).toFloat()
        )
    }
    var saving by remember { mutableStateOf(false) }

    val focal = pending.focalFraction ?: BearingMath.fallbackFocalFraction()
    val tapOffset = BearingMath.tapOffsetDeg(
        tapX.toDouble(), tapY.toDouble(), focal, photo.height.toDouble() / photo.width, pending.pitchDeg,
    )
    val centreTrue = normalize360(pending.magneticHeadingDeg + pending.declinationDeg)
    val bearing = normalize360(centreTrue + tapOffset)
    val accent = MaterialTheme.colorScheme.secondary

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Tap exactly on the object", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "For smoke, tap the base of the column where it meets the ground or treeline. That spot also lets " +
                    "the map estimate the distance from a single photo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.aspectRatio(photo.width.toFloat() / photo.height)) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Photo just taken",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { p ->
                                    tapX = (p.x / size.width).coerceIn(0f, 1f)
                                    tapY = (p.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        // Faint mark at the photo centre (where the crosshair was).
                        val c = center
                        val faint = Color.White.copy(alpha = 0.6f)
                        drawLine(faint, Offset(c.x - 16, c.y), Offset(c.x + 16, c.y), 2f)
                        drawLine(faint, Offset(c.x, c.y - 16), Offset(c.x, c.y + 16), 2f)
                        // The tapped target.
                        val t = Offset(tapX * size.width, tapY * size.height)
                        val r = 14.dp.toPx()
                        drawCircle(Color.Black.copy(alpha = 0.5f), r, t, style = Stroke(5.dp.toPx()))
                        drawCircle(accent, r, t, style = Stroke(2.5.dp.toPx()))
                        drawLine(accent, Offset(t.x - r * 1.8f, t.y), Offset(t.x - r * 0.6f, t.y), 2.dp.toPx())
                        drawLine(accent, Offset(t.x + r * 0.6f, t.y), Offset(t.x + r * 1.8f, t.y), 2.dp.toPx())
                    }
                }
            }

            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${fmt(bearing, 1)}°", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${cardinal(bearing)} true, ±${errorDeg.roundToInt()}°",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                ValueRow("Tap correction", "${signed(tapOffset)}°")
                ValueRow(
                    "Line of sight",
                    lookAngleText(TerrainMath.elevationAngleDeg(tapX.toDouble(), tapY.toDouble(), focal, photo.height.toDouble() / photo.width, pending.pitchDeg)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Error band ±${errorDeg.roundToInt()}°", Modifier.width(140.dp))
                    Slider(
                        value = errorDeg,
                        onValueChange = { errorDeg = it.roundToInt().toFloat() },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.weight(1f),
                    )
                }
                reviewWarnings(pending).forEach { WarningText(it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetake, enabled = !saving, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Retake")
                }
                Button(
                    onClick = {
                        saving = true
                        onSave(
                            buildObservation(pending, tapX.toDouble(), tapY.toDouble(), tapOffset, bearing, errorDeg.toDouble())
                        )
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(if (saving) "Saving…" else saveLabel)
                }
            }
        }
    }
}

private fun reviewWarnings(p: PendingCapture): List<String> = buildList {
    if (p.headingSpreadDeg > 3) {
        add("The phone moved while measuring (±${fmt(p.headingSpreadDeg, 0)}°). Hold still and retake for a better bearing.")
    }
    if (p.compassAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
        p.compassAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
    ) {
        add("Compass calibration was low. Calibrate (figure-8) and retake if you can.")
    }
    p.magneticFieldRatio?.takeIf { MagneticCheck.isDisturbed(it) }?.let { ratio ->
        add(
            "Magnetic interference: the field was ${MagneticCheck.describe(ratio)}, so nearby metal may have " +
                "turned the compass. The error band starts at ±15°. Move away from metal and retake if you can."
        )
    }
    if (abs(p.rollDeg) > 5) add("The phone was tilted sideways ${fmt(abs(p.rollDeg), 0)}°. Hold it level next time.")
    val gpsAccuracy = p.location.takeIf { it.hasAccuracy() }?.accuracy
    if (gpsAccuracy != null && gpsAccuracy > 50) add("GPS accuracy was poor (±${gpsAccuracy.roundToInt()} m).")
    if (p.locationAgeSeconds > 10) add("The GPS position was ${p.locationAgeSeconds} s old.")
    if (p.focalFraction == null) add("This phone didn't report its lens details, so the tap correction is approximate.")
    p.car?.let { car ->
        if (car.lineUpAgeSeconds > CarMath.LINE_UP_STALE_S) {
            add(
                "It's been ${minutesSeconds(car.lineUpAgeSeconds)} since lining up, so the gyroscope may have " +
                    "drifted (the error band allows for this). Line up again soon."
            )
        }
        if (abs(car.extrapolatedSeconds) > 2) {
            add("The GPS fix was ${fmt(car.extrapolatedSeconds, 1)} s old; the position was estimated from speed.")
        }
    }
}

private fun buildObservation(
    p: PendingCapture,
    tapX: Double,
    tapY: Double,
    tapOffset: Double,
    bearing: Double,
    errorDeg: Double,
): Observation {
    val id = "obs_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(p.timeMillis))
    val location = p.location
    return Observation(
        id = id,
        timeMillis = p.timeMillis,
        latitude = p.car?.position?.lat ?: location.latitude,
        longitude = p.car?.position?.lon ?: location.longitude,
        altitudeM = if (location.hasAltitude()) location.altitude else null,
        gpsAccuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
        magneticHeadingDeg = p.magneticHeadingDeg,
        headingSpreadDeg = p.headingSpreadDeg,
        declinationDeg = p.declinationDeg,
        pitchDeg = p.pitchDeg,
        rollDeg = p.rollDeg,
        compassAccuracy = p.compassAccuracy,
        tapX = tapX,
        tapY = tapY,
        focalLengthFraction = p.focalFraction,
        tapOffsetDeg = tapOffset,
        bearingDeg = bearing,
        errorDeg = errorDeg,
        photoFileName = "$id.jpg",
        photoWidth = p.photo.width,
        photoHeight = p.photo.height,
        headingFromCar = p.car != null,
        speedMps = p.car?.speedMps,
        lineUpAgeSeconds = p.car?.lineUpAgeSeconds,
        magneticFieldRatio = if (p.car == null) p.magneticFieldRatio else null,
    )
}
