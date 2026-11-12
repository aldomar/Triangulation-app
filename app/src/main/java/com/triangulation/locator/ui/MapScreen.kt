package com.triangulation.locator.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextOverflow
import com.triangulation.locator.data.Observation
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.data.Settings
import com.triangulation.locator.export.ShareText
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
import com.triangulation.locator.terrain.TerrainEstimator
import com.triangulation.locator.terrain.TerrainMath
import com.triangulation.locator.terrain.TerrainResult
import com.triangulation.locator.terrain.TerrainTiles
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
    /** Terrain (single-photo) estimate per bearing, same order; null when the switch is off or still working. */
    val terrain: List<TerrainResult>?,
) {
    val single: Boolean get() = observations.size == 1

    val allPoints: List<LatLon>
        get() = wedges.flatten() + ((result as? SolveResult.Fix)?.uncertaintyOutline ?: emptyList()) +
            terrain.orEmpty().filterIsInstance<TerrainResult.Found>().flatMap { it.outline }
}

private fun buildDrawing(observations: List<Observation>, terrain: List<TerrainResult>?): MapDrawing {
    val lines = observations.map { Sightline(LatLon(it.latitude, it.longitude), it.bearingDeg, it.errorDeg) }
    val result = Solver.solve(lines)
    val proj = LocalProjection.around(lines.map { it.position })
    val wedges = mutableListOf<List<LatLon>>()
    val centres = mutableListOf<List<LatLon>>()
    lines.forEachIndexed { i, line ->
        // Long enough to reach past the far end of the terrain estimate, if there is one.
        val terrainReach = (terrain?.getOrNull(i) as? TerrainResult.Found)?.let { (it.farM ?: it.maxRangeM) * 1.1 }
        val length = max(Solver.drawLengthM(result, i), terrainReach ?: 0.0)
        val o = proj.toVec(line.position)
        val half = max(line.errorDeg, 0.3)
        val arc = (0..8).map { k ->
            val b = line.bearingDeg - half + 2 * half * k / 8
            proj.toLatLon(o + Vec.ofBearing(b) * length)
        }
        wedges.add(listOf(line.position) + arc + line.position)
        centres.add(listOf(line.position, proj.toLatLon(o + Vec.ofBearing(line.bearingDeg) * length)))
    }
    return MapDrawing(observations, result, wedges, centres, terrain)
}

/** The terrain switch and its options, passed down to the results panel. */
private class TerrainControls(
    val show: Boolean,
    val tiltErrorDeg: Double,
    val downloadsOn: Boolean,
    val loading: Boolean,
    val onShow: (Boolean) -> Unit,
    val onTiltError: (Double) -> Unit,
    val onDownloads: (Boolean) -> Unit,
    val onRetry: () -> Unit,
)

@Composable
fun MapScreen(store: ObservationStore, sessionId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context.applicationContext) }
    var tilesOn by remember { mutableStateOf(settings.mapTilesEnabled == true) }
    var askAboutTiles by remember { mutableStateOf(settings.mapTilesEnabled == null) }

    val sessionFlow = remember(sessionId) { store.session(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(null)
    val observations by produceState<List<Observation>?>(null, sessionId) {
        value = store.includedObservations(sessionId)
    }

    // Terrain (single-photo) estimates: worked out only while the switch is on.
    var showTerrain by remember { mutableStateOf(settings.showTerrain) }
    var tiltErrorDeg by remember { mutableDoubleStateOf(settings.tiltErrorDeg) }
    var elevationDownloads by remember { mutableStateOf(settings.elevationDownloadsEnabled == true) }
    var askAboutElevation by remember { mutableStateOf(false) }
    var terrainRetry by remember { mutableIntStateOf(0) }
    val terrain by produceState<List<TerrainResult>?>(null, observations, showTerrain, tiltErrorDeg, elevationDownloads, terrainRetry) {
        value = null
        val chosen = observations
        if (chosen == null || !showTerrain) return@produceState
        value = chosen.map { TerrainEstimator.estimate(context.applicationContext, it, tiltErrorDeg, elevationDownloads) }
    }
    val drawing = remember(observations, terrain) { observations?.let { buildDrawing(it, terrain) } }

    // Free the decoded elevation data when leaving the map (saved files are kept).
    DisposableEffect(Unit) {
        onDispose { TerrainTiles.get(context).clearMemory() }
    }

    fun setTiles(on: Boolean) {
        tilesOn = on
        settings.mapTilesEnabled = on
    }

    val terrainControls = TerrainControls(
        show = showTerrain,
        tiltErrorDeg = tiltErrorDeg,
        downloadsOn = elevationDownloads,
        loading = showTerrain && terrain == null,
        onShow = { on ->
            showTerrain = on
            settings.showTerrain = on
            if (on && settings.elevationDownloadsEnabled == null) askAboutElevation = true
        },
        onTiltError = { v ->
            tiltErrorDeg = v
            settings.tiltErrorDeg = v
        },
        onDownloads = { on ->
            elevationDownloads = on
            settings.elevationDownloadsEnabled = on
        },
        onRetry = { terrainRetry++ },
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Top bar and bottom panel have solid backgrounds and sit above the map, so they stay
            // readable even if the map ever draws past its own area (e.g. during pinch-zoom).
            Row(
                Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(
                    session?.name ?: "Map",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("Map background", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Switch(checked = tilesOn, onCheckedChange = { setTiles(it) })
                Spacer(Modifier.width(6.dp))
                HelpButton(HelpTopic.Map)
            }

            HorizontalDivider(Modifier.zIndex(1f))
            // Clip the map to its own area: osmdroid scales its canvas while zooming, and
            // Compose doesn't clip an AndroidView by itself.
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
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
            HorizontalDivider(Modifier.zIndex(1f))

            Column(
                Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val d = drawing
                if (d == null) Text("Working it out…") else ResultsPanel(d, session?.name ?: "Triangulation", terrainControls)
            }
        }
    }

    if (askAboutElevation) {
        AlertDialog(
            onDismissRequest = { askAboutElevation = false },
            title = { Text("Download elevation data?") },
            text = {
                Text(
                    "Terrain estimates need the height of the land along each sightline. The app " +
                        "downloads this from the free \"Terrain Tiles\" collection on Amazon's open data " +
                        "service, only for the area the sightlines cross. Amazon will see which area and " +
                        "your internet address. Nothing else is sent: no bearings, photos or position.\n\n" +
                        "Downloaded areas are kept on the phone (up to about 100 MB), so they work later " +
                        "without signal. You can change this any time with the switch under Terrain estimate."
                )
            },
            confirmButton = {
                TextButton(onClick = { terrainControls.onDownloads(true); askAboutElevation = false }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { terrainControls.onDownloads(false); askAboutElevation = false }) { Text("No thanks") }
            },
        )
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
        modifier = Modifier.fillMaxSize().clipToBounds(),
        update = { map ->
            // No downloads at all while the background is off.
            map.setUseDataConnection(tilesOn)
            map.overlayManager.tilesOverlay.isEnabled = tilesOn

            map.overlays.clear()
            if (!tilesOn) map.overlays.add(LatLonGridlineOverlay2())
            addDrawingOverlays(map, drawing, density)
            map.invalidate()

            // Re-zoom for new bearings or a new terrain result, not while a terrain estimate is being worked out.
            val last = zoomedTo
            if (last == null || last.observations != drawing.observations || (drawing.terrain != null && drawing.terrain != last.terrain)) {
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

    // Terrain estimates: dashed near–far areas along each sightline. With one bearing, it is the answer.
    val terrainDots = mutableListOf<MapDot>()
    drawing.terrain?.forEachIndexed { i, r ->
        val found = r as? TerrainResult.Found ?: return@forEachIndexed
        val color = if (drawing.single) FIX_COLOR else lineColor(i)
        map.overlays.add(Polygon(map).apply {
            points = found.outline.geo()
            fillPaint.color = withAlpha(color, 0x40)
            outlinePaint.color = color
            outlinePaint.strokeWidth = 2f * density
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
            infoWindow = null
        })
        terrainDots += MapDot(
            GeoPoint(found.point.lat, found.point.lon),
            color,
            if (drawing.single) "Target (terrain)" else "${lineLabel(i)} ground",
            radiusDp = if (drawing.single) 8f else 6f,
        )
    }

    val dots = drawing.observations.mapIndexed { i, obs ->
        MapDot(GeoPoint(obs.latitude, obs.longitude), lineColor(i), lineLabel(i))
    } + terrainDots +
        listOfNotNull(fix?.let { MapDot(GeoPoint(it.point.lat, it.point.lon), FIX_COLOR, "Target", radiusDp = 8f) })
    map.overlays.add(DotsOverlay(dots, density))
}

private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

// ---------- Results panel ----------

@Composable
private fun ResultsPanel(drawing: MapDrawing, sessionName: String, t: TerrainControls) {
    val singleTerrain = if (drawing.single) drawing.terrain?.firstOrNull() else null
    val singleFound = singleTerrain as? TerrainResult.Found
    val shareText = remember(drawing, sessionName) {
        ShareText.build(sessionName, drawing.observations, drawing.result, drawing.terrain)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                drawing.result is SolveResult.Fix -> "Estimated location"
                singleFound != null -> "Estimated location (terrain)"
                else -> "No location yet"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        ExportButton(sessionName = sessionName, loadObservations = { drawing.observations }, terrain = drawing.terrain)
    }
    val result = drawing.result
    when {
        result is SolveResult.Fix -> {
            CoordinatesRow(result.point, shareText)
            ValueRow(
                "Uncertainty",
                result.uncertaintyRadiusM?.let { "within about ${distanceText(it)}" } ?: "too large to draw",
            )
            ValueRow("Best crossing angle", "${result.bestCrossingAngleDeg.roundToInt()}°")
            result.rmsMissM?.let { ValueRow("Lines miss the point by", "about ${distanceText(it)} on average") }
            result.warnings.forEach { WarningText(warningText(it)) }
        }
        drawing.single -> when {
            singleFound != null -> {
                CoordinatesRow(singleFound.point, shareText)
                ValueRow("Distance", "about ${distanceText(singleFound.distanceM)}")
                ValueRow("Likely between", rangeText(singleFound))
                if (singleFound.veryUncertain) WarningText(VERY_UNCERTAIN_TEXT)
            }
            !t.show -> Text(
                "With one bearing, the app can still estimate the distance from the shape of the land. " +
                    "Turn on \"Terrain estimate\" below. For a better fix, take a second bearing from somewhere else.",
                style = MaterialTheme.typography.bodyMedium,
            )
            t.loading -> Text("Working out the terrain estimate…")
            singleTerrain != null -> WarningText(terrainProblemText(singleTerrain).replaceFirstChar { it.uppercase() })
        }
        result is SolveResult.NoFix -> WarningText(noFixText(result))
    }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    TerrainControlsSection(t, drawing.terrain)

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
                    (fix?.let { "  ·  ${distanceText(it.distancesM[i])} away" } ?: ""),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(obs.timeMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (t.show && !drawing.single) {
            val r = drawing.terrain?.getOrNull(i)
            Text(
                when (r) {
                    null -> "Terrain: working it out…"
                    is TerrainResult.Found -> "Terrain: meets the ground about ${distanceText(r.distanceM)} away (${rangeText(r)})"
                    else -> "Terrain: ${terrainProblemText(r)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp),
            )
        }
    }
    Text(
        "An estimate to guide where to look, not a location to dispatch to. For a real fire, call 911 or your local fire agency.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Coordinates in large text, with Copy, Share and Open-in-map-app buttons.
 * Share and Open hand the text to an app the user picks; this app sends nothing itself.
 */
@Composable
private fun CoordinatesRow(point: LatLon, shareText: String?) {
    val context = LocalContext.current
    val coords = "${fmt(point.lat, 6)}, ${fmt(point.lon, 6)}"
    Text(coords, style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val padding = PaddingValues(horizontal = 14.dp)
        OutlinedButton(onClick = {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Estimated location", coords))
            Toast.makeText(context, "Coordinates copied", Toast.LENGTH_SHORT).show()
        }, contentPadding = padding) { Text("Copy") }
        if (shareText != null) {
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, shareText)
                context.startActivity(Intent.createChooser(send, "Share estimated location"))
            }, contentPadding = padding) { Text("Share") }
        }
        // weight(fill = false): on a narrow phone this button wraps its text instead of being cut off.
        TextButton(modifier = Modifier.weight(1f, fill = false), onClick = {
            val ll = "${fmt(point.lat, 6)},${fmt(point.lon, 6)}"
            val geo = Uri.parse("geo:$ll?q=$ll(${Uri.encode("Estimated location")})")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, geo))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No map app on this phone can open it", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Open in map app") }
    }
}

/** The "Terrain estimate" switch, camera tilt error choice and elevation download switch. */
@Composable
private fun TerrainControlsSection(t: TerrainControls, terrain: List<TerrainResult>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Terrain estimate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Follows each sightline down to the ground using its photo's tilt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = t.show, onCheckedChange = t.onShow)
    }
    if (!t.show) return

    Text("How far off the camera tilt might be", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TerrainMath.TILT_ERROR_CHOICES.forEach { v ->
            val label = "±${fmt(v, if (v % 1.0 == 0.0) 0 else 1)}°"
            val padding = PaddingValues(horizontal = 14.dp)
            if (v == t.tiltErrorDeg) {
                Button(onClick = {}, contentPadding = padding, modifier = Modifier.height(36.dp)) { Text(label) }
            } else {
                OutlinedButton(onClick = { t.onTiltError(v) }, contentPadding = padding, modifier = Modifier.height(36.dp)) {
                    Text(label)
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Download elevation data", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = t.downloadsOn, onCheckedChange = t.onDownloads)
    }
    val failed = terrain.orEmpty().any { it is TerrainResult.NoData && !it.downloadsOff }
    if (failed) {
        TextButton(onClick = t.onRetry) { Text("Try the download again") }
    }
    Text(
        "Assumes you tapped where the smoke meets the ground. Ignores trees and buildings, which can make " +
            "the real spot nearer. Elevation: Terrain Tiles (SRTM and others) via AWS Open Data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val VERY_UNCERTAIN_TEXT =
    "The camera was nearly level, so a small tilt error moves this point a long way. Treat the distance " +
        "as rough, or take a second bearing from somewhere else."

private fun rangeText(f: TerrainResult.Found): String =
    "${distanceText(f.nearM)} to ${f.farM?.let(::distanceText) ?: "beyond ${distanceText(f.maxRangeM)}"}"

private fun terrainProblemText(r: TerrainResult): String = when (r) {
    is TerrainResult.Found -> ""
    is TerrainResult.NoGround ->
        "the line of sight doesn't meet the ground within ${distanceText(r.maxRangeM)} " +
            "(it points ${signed(r.elevationAngleDeg)}° from level). Was the tap above the horizon?"
    is TerrainResult.NoData ->
        if (r.downloadsOff) "no elevation data saved for this area. Turn on \"Download elevation data\"."
        else "couldn't get elevation data. Check the internet connection and try again."
    TerrainResult.NoTilt -> "typed-in bearings have no camera tilt, so the shape of the land can't place them."
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
            "moved between shots. Try unticking one in the session list."
}

private fun noFixText(r: SolveResult.NoFix) = when (r.reason) {
    NoFixReason.TooFewBearings -> "Tick at least two bearings in the session list."
    NoFixReason.ObserversTooClose ->
        "All bearings were taken from nearly the same spot. Take another from somewhere else: the further away, the better."
    NoFixReason.Parallel ->
        "The sightlines are almost parallel, so they never cross. Take a bearing from a place well off to the side."
    NoFixReason.BehindObserver ->
        "Sightline ${r.observerIndex?.let(::lineLabel) ?: ""} points away from where the others cross. " +
            "Check that bearing, or untick it in the session list."
}

