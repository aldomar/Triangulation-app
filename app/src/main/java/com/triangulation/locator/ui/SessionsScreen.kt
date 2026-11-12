package com.triangulation.locator.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.data.SessionSummary
import com.triangulation.locator.solver.LatLon
import com.triangulation.locator.solver.SolveResult
import com.triangulation.locator.solver.Sightline
import com.triangulation.locator.solver.Solver
import com.triangulation.locator.ui.theme.StatusColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** History: every session, newest first. Tap one to see its bearings. */
@Composable
fun SessionsScreen(
    store: ObservationStore,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val summariesFlow = remember(store) { store.sessionSummaries() }
    val summaries by summariesFlow.collectAsStateWithLifecycle(null)
    val activeId by produceState<Long?>(null, summaries) { value = store.activeSession()?.id }
    var naming by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenHeader("History", onBack, HelpTopic.History) }
                TextButton(onClick = { naming = true }) { Text("New session") }
            }
            val list = summaries
            when {
                list == null -> Text("Loading…", Modifier.padding(16.dp))
                list.isEmpty() -> Text(
                    "Nothing saved yet. Go back and take a bearing: it starts a session automatically. " +
                        "A session holds all the bearings of one object, such as one smoke column.",
                    Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Each session holds the bearings of one object. New bearings go into the active session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(list, key = { it.id }) { summary ->
                        SessionRow(store, summary, active = summary.id == activeId, onClick = { onOpenSession(summary.id) })
                    }
                }
            }
        }
    }

    if (naming) {
        SessionNameDialog(
            title = "New session",
            initial = ObservationStore.defaultSessionName(),
            confirmLabel = "Start",
            onConfirm = { name ->
                naming = false
                scope.launch { onOpenSession(store.startSession(name).id) }
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun SessionRow(store: ObservationStore, summary: SessionSummary, active: Boolean, onClick: () -> Unit) {
    // Re-solved whenever the session's bearings change (the summary changes with them).
    val fixText by produceState<String?>(null, summary) {
        val observations = store.includedObservations(summary.id)
        value = if (observations.size < 2) {
            null
        } else {
            val lines = observations.map { Sightline(LatLon(it.latitude, it.longitude), it.bearingDeg, it.errorDeg) }
            when (val r = Solver.solve(lines)) {
                is SolveResult.Fix -> "Location: ${fmt(r.point.lat, 5)}, ${fmt(r.point.lon, 5)}" +
                    (r.uncertaintyRadiusM?.let { "  ±${distanceText(it)}" } ?: "")
                is SolveResult.NoFix -> "No location yet"
            }
        }
    }
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge("Active", StatusColors.Good)
                }
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(summary.lastMillis ?: summary.createdMillis)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when (summary.bearingCount) {
                    0 -> "No bearings yet"
                    1 -> "1 bearing"
                    else -> "${summary.bearingCount} bearings, ${summary.includedCount} ticked"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fixText?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Asks for a session name. Used for "New session" and "Rename". */
@Composable
fun SessionNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Give it a name you'll recognise, e.g. \"Smoke north of town\".")
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** E.g. "350 m" or "2.4 km". */
fun distanceText(m: Double): String =
    if (m < 1000) "${(m / 10).roundToInt() * 10} m" else "${fmt(m / 1000, 1)} km"
