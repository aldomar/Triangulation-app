package com.triangulation.locator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.triangulation.locator.data.Observation
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.data.Settings
import com.triangulation.locator.map.DotsOverlay
import com.triangulation.locator.map.MapDot
import com.triangulation.locator.map.configureMaps
import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.LocalProjection
import com.triangulation.locator.solver.NoFixReason
import com.triangulation.locator.solver.SolveResult
import com.triangulation.locator.solver.SolveWarning
import com.triangulation.locator.solver.Sightline
import com.triangulation.locator.solver.Solver
import com.triangulation.locator.solver.Vec
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/** Distinct sightline colours (A, B, C…), readable on the map. */
private val LINE_COLORS = intArrayOf(
    0xFF2F6FDE.toInt(), 0xFF1A9E8F.toInt(), 0xFF8A4FD8.toInt(),
    0xFFC2417F.toInt(), 0xFF3D8B37.toInt(), 0xFFB07A00.toInt(),
)
private val FIX_COLOR = 0xFFFF7A3D.toInt()
private val PAPER = AndroidColor.rgb(242, 240, 234)

private fun lineColor(i: Int) = LINE_COLORS[i % LINE_COLORS.size]
private fun lineLabel(i: Int) = ('A' + i).toString()

/** Everything drawn on the map, computed once per set of bearings. */
private class MapDrawing(
    val observations: List<Observation>,
    val result: SolveResult,
    val wedges: List<List<LatLon>>,
    val centreLines: List<List<LatLon>>,
) {
    val allPoints: List<LatLon>
        get() = wedges.flatten() + ((result as? SolveResult.Fix)?.uncertaintyOutline ?: emptyList())
}

private fun buildDrawing(observations: List<Observation>): MapDrawing {
    val lines = observations.map { Sightline(LatLon(it.latitude, it.longitude), it.bearingDeg, it.errorDeg) }
    val result = Solver.solve(lines)
    val proj = LocalProjection.around(lines.map { it.position })
    val fix = result as? SolveResult.Fix

    val wedges = mutableListOf<List<LatLon>>()
    val centres = mutableListOf<List<LatLon>>()
    lines.forEachIndexed { i, line ->
        // Draw each sightline a bit past the target, or 20 km if there's no fix.
        val length = if (fix != null) {
            max(fix.distancesM[i] * 1.4, fix.distancesM[i] + 2 * (fix.uncertaintyRadiusM ?: 0.0))
        } else {
            20_000.0
        }
        val o = proj.toVec(line.position)
        val half = max(line.errorDeg, 0.3)
        val arc = (0..8).map { k ->
            val b = line.bearingDeg - half + 2 * half * k / 8
            proj.toLatLon(o + Vec.ofBearing(b) * length)
        }
        wedges.add(listOf(line.position) + arc + line.position)
        centres.add(listOf(line.position, proj.toLatLon(o + Vec.ofBearing(line.bearingDeg) * length)))
    }
    return MapDrawing(observations, result, wedges, centres)
}

@Composable
fun MapScreen(store: ObservationStore, observationIds: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context.applicationContext) }
    var tilesOn by remember { mutableStateOf(settings.mapTilesEnabled == true) }
    var askAboutTiles by remember { mutableStateOf(settings.mapTilesEnabled == null) }

    val drawing by produceState<MapDrawing?>(null, observationIds) {
        val chosen = store.list().filter { it.id in observationIds }.sortedBy { it.timeMillis }
        value = buildDrawing(chosen)
    }

    fun setTiles(on: Boolean) {
        tilesOn = on
        settings.mapTilesEnabled = on
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(
                    "Map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text("Map background", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Switch(checked = tilesOn, onCheckedChange = { setTiles(it) })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                drawing?.let { TriangulationMap(it, tilesOn) }
                Text(
                    if (tilesOn) "© OpenStreetMap contributors" else "Map background off: grid only",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Column(
                Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val d = drawing
                if (d == null) Text("Working it out…") else ResultsPanel(d)
            }
        }
    }

    if (askAboutTiles) {
        AlertDialog(
            onDismissRequest = { askAboutTiles = false },
            title = { Text("Show map background?") },
            text = {
                Text(
                    "Map images come from OpenStreetMap over the internet. Their servers will see which " +
                        "area you are looking at and your internet address. Nothing else is sent: no " +
                        "bearings, photos or position. The app works without it, showing a plain grid " +
                        "instead.\n\nYou can change this any time with the switch at the top of the map."
                )
            },
            confirmButton = {
                TextButton(onClick = { setTiles(true); askAboutTiles = false }) { Text("Show map") }
            },
            dismissButton = {
                TextButton(onClick = { setTiles(false); askAboutTiles = false }) { Text("No thanks") }
            },
        )
    }
}

/** The osmdroid map with wedges, observer dots, the estimated point and its uncertainty area. */
@Composable
private fun TriangulationMap(drawing: MapDrawing, tilesOn: Boolean) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        configureMaps(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            setBackgroundColor(PAPER)
            overlayManager.tilesOverlay.setLoadingBackgroundColor(PAPER)
            overlayManager.tilesOverlay.setLoadingLineColor(PAPER)
        }
    }
    var zoomedTo by remember { mutableStateOf<MapDrawing?>(null) }

    // Map tile loading and threads run only while the screen is visible.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            // No downloads at all while the background is off.
            map.setUseDataConnection(tilesOn)
            map.overlayManager.tilesOverlay.isEnabled = tilesOn

            map.overlays.clear()
            if (!tilesOn) map.overlays.add(LatLonGridlineOverlay2())
            addDrawingOverlays(map, drawing, density)
            map.invalidate()

            if (zoomedTo !== drawing) {
                zoomedTo = drawing
                val points = drawing.allPoints.map { GeoPoint(it.lat, it.lon) }
                if (points.isNotEmpty()) {
                    val box = BoundingBox.fromGeoPointsSafe(points)
                    val padding = (32 * density).roundToInt()
                    if (map.width > 0) {
                        map.zoomToBoundingBox(box, false, padding)
                    } else {
                        map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, padding) }
                    }
                }
            }
        },
    )
}

private fun addDrawingOverlays(map: MapView, drawing: MapDrawing, density: Float) {
    fun List<LatLon>.geo() = map { GeoPoint(it.lat, it.lon) }

    drawing.wedges.forEachIndexed { i, wedge ->
        map.overlays.add(Polygon(map).apply {
            points = wedge.geo()
            fillPaint.color = withAlpha(lineColor(i), 0x33)
            outlinePaint.color = withAlpha(lineColor(i), 0x99)
            outlinePaint.strokeWidth = 1.5f * density
            infoWindow = null
        })
    }
    drawing.centreLines.forEachIndexed { i, line ->
        map.overlays.add(Polyline(map).apply {
            setPoints(line.geo())
            outlinePaint.color = lineColor(i)
            outlinePaint.strokeWidth = 2.5f * density
            outlinePaint.strokeCap = Paint.Cap.ROUND
            infoWindow = null
        })
    }
    val fix = drawing.result as? SolveResult.Fix
    fix?.uncertaintyOutline?.let { outline ->
        map.overlays.add(Polygon(map).apply {
            points = outline.geo()
            fillPaint.color = withAlpha(FIX_COLOR, 0x55)
            outlinePaint.color = FIX_COLOR
            outlinePaint.strokeWidth = 2f * density
            infoWindow = null
        })
    }
    val dots = drawing.observations.mapIndexed { i, obs ->
        MapDot(GeoPoint(obs.latitude, obs.longitude), lineColor(i), lineLabel(i))
    } + listOfNotNull(fix?.let { MapDot(GeoPoint(it.point.lat, it.point.lon), FIX_COLOR, "Target", radiusDp = 8f) })
    map.overlays.add(DotsOverlay(dots, density))
}

private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

// ---------- Results panel ----------

@Composable
private fun ResultsPanel(drawing: MapDrawing) {
    val context = LocalContext.current
    when (val result = drawing.result) {
        is SolveResult.Fix -> {
            Text("Estimated location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val coords = "${fmt(result.point.lat, 6)}, ${fmt(result.point.lon, 6)}"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(coords, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Estimated location", coords))
                    Toast.makeText(context, "Coordinates copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
            }
            ValueRow(
                "Uncertainty",
                result.uncertaintyRadiusM?.let { "within about ${distance(it)}" } ?: "too large to draw",
            )
            ValueRow("Best crossing angle", "${result.bestCrossingAngleDeg.roundToInt()}°")
            result.rmsMissM?.let { ValueRow("Lines miss the point by", "about ${distance(it)} on average") }
            result.warnings.forEach { WarningText(warningText(it)) }
        }
        is SolveResult.NoFix -> {
            Text("No location yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            WarningText(noFixText(result))
        }
    }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    val fix = drawing.result as? SolveResult.Fix
    drawing.observations.forEachIndexed { i, obs ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(Color(lineColor(i)), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${lineLabel(i)}  ${fmt(obs.bearingDeg, 1)}° ±${obs.errorDeg.roundToInt()}°" +
                    (fix?.let { "  ·  ${distance(it.distancesM[i])} away" } ?: ""),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(obs.timeMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Text(
        "An estimate to guide where to look, not a location to dispatch to. For a real fire, call 911 or your local fire agency.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun warningText(w: SolveWarning) = when (w) {
    SolveWarning.ShallowAngle ->
        "The sightlines cross at a shallow angle (under ${Solver.SHALLOW_ANGLE_DEG.roundToInt()}°), so the point " +
            "could be anywhere along the lines. Take another bearing from a place well off to the side."
    SolveWarning.Far ->
        "The object is over ${(Solver.FAR_M / 1000).roundToInt()} km away. Accuracy drops at this distance."
    SolveWarning.UncertaintyUnbounded ->
        "The error bands are too wide for the area to close: the object could be much further away. " +
            "Calibrate the compass and retake, or add a bearing from another direction."
    SolveWarning.BearingsDisagree ->
        "The bearings disagree more than their error bands allow. One may be wrong, or the smoke " +
            "moved between shots. Try unticking one on the Saved screen."
}

private fun noFixText(r: SolveResult.NoFix) = when (r.reason) {
    NoFixReason.TooFewBearings -> "Select at least two bearings on the Saved screen."
    NoFixReason.ObserversTooClose ->
        "All bearings were taken from nearly the same spot. Take another from somewhere else: the further away, the better."
    NoFixReason.Parallel ->
        "The sightlines are almost parallel, so they never cross. Take a bearing from a place well off to the side."
    NoFixReason.BehindObserver ->
        "Sightline ${r.observerIndex?.let(::lineLabel) ?: ""} points away from where the others cross. " +
            "Check that bearing, or untick it on the Saved screen."
}

private fun distance(m: Double): String =
    if (m < 1000) "${(m / 10).roundToInt() * 10} m" else "${fmt(m / 1000, 1)} km"
