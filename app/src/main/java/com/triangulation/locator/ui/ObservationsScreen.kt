package com.triangulation.locator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.triangulation.locator.data.Observation
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.ui.theme.StatusColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** List of saved bearings. Phase 3 will draw these on a map. */
@Composable
fun ObservationsScreen(store: ObservationStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var observations by remember { mutableStateOf<List<Observation>?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Observation?>(null) }

    suspend fun reload() {
        observations = store.list()
    }
    LaunchedEffect(Unit) { reload() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader("Saved bearings", onBack) }
                if (!observations.isNullOrEmpty()) {
                    TextButton(onClick = { confirmDeleteAll = true }) { Text("Delete all") }
                }
            }
            val list = observations
            when {
                list == null -> Text("Loading…", Modifier.padding(16.dp))
                list.isEmpty() -> Text(
                    "No bearings saved yet. Go back and take one. You'll need at least two, " +
                        "from different places, to locate an object.",
                    Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "${list.size} saved. Best results: two or more bearings from places far apart, " +
                                "with sightlines crossing at a wide angle.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(list, key = { it.id }) { obs ->
                        ObservationRow(store, obs, onDelete = { toDelete = obs })
                    }
                }
            }
        }
    }

    toDelete?.let { obs ->
        ConfirmDialog(
            text = "Delete this bearing and its photo?",
            onConfirm = {
                toDelete = null
                scope.launch { store.delete(obs); reload() }
            },
            onDismiss = { toDelete = null },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            text = "Delete all saved bearings and photos? This can't be undone.",
            onConfirm = {
                confirmDeleteAll = false
                scope.launch { store.deleteAll(); reload() }
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun ObservationRow(store: ObservationStore, obs: Observation, onDelete: () -> Unit) {
    val thumbPx = with(LocalDensity.current) { 72.dp.roundToPx() }
    val thumbnail by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, obs.id) {
        value = store.loadThumbnail(obs, thumbPx)?.asImageBitmap()
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumbModifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
            thumbnail?.let {
                Image(it, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = thumbModifier)
            } ?: Box(thumbModifier.background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${fmt(obs.bearingDeg, 1)}° ${cardinal(obs.bearingDeg)}  ±${obs.errorDeg.roundToInt()}°",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(obs.timeMillis)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "${fmt(obs.latitude, 5)}, ${fmt(obs.longitude, 5)}" +
                        (obs.gpsAccuracyM?.let { "  ±${it.roundToInt()} m" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val (label, color) = compassAccuracyLabel(obs.compassAccuracy)
                Text(
                    "Compass: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (color == StatusColors.Good) MaterialTheme.colorScheme.onSurfaceVariant else color,
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
