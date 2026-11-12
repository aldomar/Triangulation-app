package com.triangulation.locator.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.triangulation.locator.data.Settings
import com.triangulation.locator.map.DotsOverlay
import com.triangulation.locator.map.MapDot
import com.triangulation.locator.map.configureMaps
import com.triangulation.locator.solver.LatLon
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay2
import android.graphics.Color as AndroidColor

private val PIN_COLOR = 0xFFFF7A3D.toInt()

/**
 * Full-screen map for choosing a point by tapping it. Used for typed-in bearings (where the
 * observer stood) and landmark checks (where the landmark is).
 *
 * Follows the map background setting: tiles only if the user has turned them on, otherwise a
 * plain grid and no network use.
 */
@Composable
fun PointPicker(
    title: String,
    initial: LatLon?,
    onPick: (LatLon) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilesOn = remember { Settings(context.applicationContext).mapTilesEnabled == true }
    var chosen by remember { mutableStateOf(initial) }

    BackHandler(onBack = onCancel)

    val mapView = remember {
        configureMaps(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            setBackgroundColor(AndroidColor.rgb(242, 240, 234))
            setUseDataConnection(tilesOn)
            overlayManager.tilesOverlay.isEnabled = tilesOn
            controller.setZoom(if (initial != null) 13.0 else 3.0)
            controller.setCenter(GeoPoint(initial?.lat ?: 20.0, initial?.lon ?: 0.0))
        }
    }

    // Map threads run only while the screen is visible.
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier
                    .zIndex(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("← Back") }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(Modifier.zIndex(1f))
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    update = { map ->
                        map.overlays.clear()
                        if (!tilesOn) map.overlays.add(LatLonGridlineOverlay2())
                        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                chosen = LatLon(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        }))
                        chosen?.let {
                            map.overlays.add(DotsOverlay(listOf(MapDot(GeoPoint(it.lat, it.lon), PIN_COLOR, null, 8f)), density))
                        }
                        map.invalidate()
                    },
                )
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val point = chosen
                Text(
                    if (point == null) "Tap the map to place the point. Pinch to zoom, drag to move."
                    else "Chosen: ${fmt(point.lat, 6)}, ${fmt(point.lon, 6)}. Tap elsewhere to move it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!tilesOn) {
                    Text(
                        "The map background is off, so only a grid shows. You can turn it on from a session's map, " +
                            "or type the coordinates instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { point?.let(onPick) },
                    enabled = point != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Use this point") }
            }
        }
    }
}
