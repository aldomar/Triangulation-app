package com.triangulation.locator.ui

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.triangulation.locator.data.Observation
import com.triangulation.locator.export.ExportFormat
import com.triangulation.locator.export.SessionExport
import com.triangulation.locator.terrain.TerrainResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Export…" button: asks GeoJSON or GPX, then opens Android's "Save to" picker so the user
 * chooses where the file goes (phone folder, SD card, or a cloud drive app). Nothing is sent
 * anywhere unless the user picks a cloud location themselves. Needs no permissions.
 *
 * @param loadObservations the ticked bearings to export, oldest first.
 * @param terrain terrain estimates for those bearings (same order), if shown on the map.
 */
@Composable
fun ExportButton(
    sessionName: String,
    loadObservations: suspend () -> List<Observation>,
    modifier: Modifier = Modifier,
    terrain: List<TerrainResult>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    // Kept across the trip to the picker, which can take a while.
    var pendingFormat by rememberSaveable { mutableStateOf<String?>(null) }

    // octet-stream stops the picker from changing the .geojson/.gpx file extension.
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val format = pendingFormat?.let { name -> ExportFormat.entries.firstOrNull { it.name == name } }
        pendingFormat = null
        if (uri == null || format == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = SessionExport.build(format, sessionName, loadObservations(), System.currentTimeMillis(), terrain)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("No output stream for $uri")
                }
                Toast.makeText(context, "${format.label} file saved", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Export", "Export failed", e)
                Toast.makeText(context, "Couldn't save the file. Try another place.", Toast.LENGTH_LONG).show()
            }
        }
    }

    OutlinedButton(onClick = { choosing = true }, modifier = modifier) { Text("Export…") }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Export to a file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Saves the ticked bearings, their sightlines and the estimated location (with its " +
                            "uncertainty area) as a file other map apps can open. Terrain estimates are included when " +
                            "they are switched on. Photos aren't included."
                    )
                    Text("GeoJSON: for QGIS, geojson.io, Google Earth and most GIS tools.")
                    Text("GPX: for hiking and GPS apps such as OsmAnd, Gaia GPS or caltopo.")
                    Text("You choose where the file is saved. The app doesn't send it anywhere.")
                }
            },
            confirmButton = {
                ExportFormat.entries.forEach { format ->
                    TextButton(onClick = {
                        choosing = false
                        pendingFormat = format.name
                        saveLauncher.launch(SessionExport.fileName(sessionName, format))
                    }) { Text(format.label) }
                }
            },
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } },
        )
    }
}
