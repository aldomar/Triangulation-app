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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triangulation.locator.data.BearingMath
import com.triangulation.locator.data.Observation
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.data.Session
import com.triangulation.locator.ui.theme.StatusColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * One session's bearings. Tick the ones to use, then "Show on map". Ticks are saved, so they
 * survive closing the app. Also: rename, delete, export, move bearings to another session.
 */
@Composable
fun SessionScreen(
    store: ObservationStore,
    sessionId: Long,
    onShowMap: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sessionFlow = remember(sessionId) { store.session(sessionId) }
    val observationsFlow = remember(sessionId) { store.observations(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(null)
    val observations by observationsFlow.collectAsStateWithLifecycle(null)
    var activeId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(session) { activeId = store.activeSession()?.id }

    var renaming by remember { mutableStateOf(false) }
    var confirmDeleteSession by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Observation?>(null) }
    var toMove by remember { mutableStateOf<Observation?>(null) }

    // Leave if the session doesn't exist (e.g. just deleted).
    LaunchedEffect(sessionFlow) {
        sessionFlow.collect { if (it == null) onBack() }
    }

    val name = session?.name ?: ""
    val list = observations
    val included = list.orEmpty().count { it.included }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (activeId == sessionId) StatusBadge("Active", StatusColors.Good)
            }
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { renaming = true }, enabled = session != null) { Text("Rename") }
                TextButton(onClick = { confirmDeleteSession = true }, enabled = session != null) { Text("Delete") }
                if (activeId != null && activeId != sessionId) {
                    TextButton(onClick = {
                        store.makeActive(sessionId)
                        activeId = sessionId
                    }) { Text("Make active") }
                }
                Spacer(Modifier.weight(1f))
                if (included > 0) {
                    ExportButton(sessionName = name, loadObservations = { store.includedObservations(sessionId) })
                }
            }

            when {
                list == null -> Text("Loading…", Modifier.padding(16.dp))
                list.isEmpty() -> Text(
                    "No bearings in this session yet. New bearings go into the active session. You'll need " +
                        "at least two, from different places, to locate the object.",
                    Modifier.padding(16.dp),
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                "${list.size} bearings. Ticked ones are used on the map and in exports. " +
                                    "Best results: places far apart, with sightlines crossing at a wide angle. One bearing can " +
                                    "also be placed using the shape of the land (Terrain estimate on the map).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(list, key = { it.id }) { obs ->
                            ObservationRow(
                                store = store,
                                obs = obs,
                                onCheckedChange = { on -> scope.launch { store.setIncluded(obs, on) } },
                                onMove = { toMove = obs },
                                onDelete = { toDelete = obs },
                            )
                        }
                    }
                    Button(
                        onClick = onShowMap,
                        enabled = included >= 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(52.dp),
                    ) {
                        Text(
                            when {
                                included >= 2 -> "Show $included bearings on map"
                                included == 1 -> "Show 1 bearing on map"
                                else -> "Tick bearings to show on the map"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        SessionNameDialog(
            title = "Rename session",
            initial = name,
            confirmLabel = "Save",
            onConfirm = { newName ->
                renaming = false
                scope.launch { store.renameSession(sessionId, newName) }
            },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDeleteSession) {
        ConfirmDialog(
            text = "Delete \"$name\" with all ${list?.size ?: 0} bearings and photos? This can't be undone.",
            onConfirm = {
                confirmDeleteSession = false
                scope.launch { store.deleteSession(sessionId) }
            },
            onDismiss = { confirmDeleteSession = false },
        )
    }
    toDelete?.let { obs ->
        ConfirmDialog(
            text = "Delete this bearing and its photo?",
            onConfirm = {
                toDelete = null
                scope.launch { store.delete(obs) }
            },
            onDismiss = { toDelete = null },
        )
    }
    toMove?.let { obs ->
        MoveDialog(
            store = store,
            currentSessionId = sessionId,
            onMove = { target ->
                toMove = null
                scope.launch {
                    val targetId = target?.id ?: store.startSession(ObservationStore.defaultSessionName(), makeActive = false).id
                    store.move(obs, targetId)
                }
            },
            onDismiss = { toMove = null },
        )
    }
}

@Composable
private fun ObservationRow(
    store: ObservationStore,
    obs: Observation,
    onCheckedChange: (Boolean) -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val thumbPx = with(LocalDensity.current) { 72.dp.roundToPx() }
    val thumbnail by produceState<ImageBitmap?>(null, obs.id) {
        value = store.loadThumbnail(obs, thumbPx)?.asImageBitmap()
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = obs.included, onCheckedChange = onCheckedChange)
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
                Text(
                    lookAngleText(BearingMath.elevationAngleDeg(obs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (obs.headingFromCar) {
                    Text(
                        "Car mode" +
                            (obs.speedMps?.let { ", ${(it * 3.6).roundToInt()} km/h" } ?: "") +
                            (obs.lineUpAgeSeconds?.let { ", lined up ${minutesSeconds(it)} before" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val (label, color) = compassAccuracyLabel(obs.compassAccuracy)
                    Text(
                        "Compass: $label",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (color == StatusColors.Good) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onMove) { Text("Move") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

/** Pick another session to move a bearing into. A null target means "a new session". */
@Composable
private fun MoveDialog(
    store: ObservationStore,
    currentSessionId: Long,
    onMove: (Session?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sessionsFlow = remember(store) { store.sessions() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move bearing to…") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sessions.filter { it.id != currentSessionId }.forEach { s ->
                    TextButton(onClick = { onMove(s) }, modifier = Modifier.fillMaxWidth()) {
                        Text(s.name, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = { onMove(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ A new session", Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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

/** "Looking 1.2° down" / "Looking 0.5° up" / "Looking level" for a line-of-sight angle. */
fun lookAngleText(angleDeg: Double): String = when {
    angleDeg <= -0.05 -> "Looking ${fmt(-angleDeg, 1)}° down"
    angleDeg >= 0.05 -> "Looking ${fmt(angleDeg, 1)}° up"
    else -> "Looking level"
}
