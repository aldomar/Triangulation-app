package com.triangulation.locator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.ui.CaptureScreen
import com.triangulation.locator.ui.MapScreen
import com.triangulation.locator.ui.ObservationsScreen
import com.triangulation.locator.ui.SensorTestScreen
import com.triangulation.locator.ui.theme.TriangulationTheme

private enum class Screen { Capture, Sensors, Observations, Map }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriangulationTheme {
                val context = LocalContext.current
                val store = remember { ObservationStore(context.applicationContext) }
                var screen by rememberSaveable { mutableStateOf(Screen.Capture) }
                // Bearings ticked on the Saved screen (null = not chosen yet).
                var selectedIds by remember { mutableStateOf<Set<String>?>(null) }
                val backToCapture = { screen = Screen.Capture }
                val backToSaved = { screen = Screen.Observations }

                when (screen) {
                    Screen.Capture -> Unit
                    Screen.Map -> BackHandler(onBack = backToSaved)
                    else -> BackHandler(onBack = backToCapture)
                }

                when (screen) {
                    Screen.Capture -> CaptureScreen(
                        store = store,
                        onOpenSensors = { screen = Screen.Sensors },
                        onOpenObservations = { screen = Screen.Observations },
                    )
                    Screen.Sensors -> SensorTestScreen(onBack = backToCapture)
                    Screen.Observations -> ObservationsScreen(
                        store = store,
                        selectedIds = selectedIds,
                        onSelectionChange = { selectedIds = it },
                        onShowMap = { screen = Screen.Map },
                        onBack = backToCapture,
                    )
                    Screen.Map -> MapScreen(
                        store = store,
                        observationIds = selectedIds.orEmpty().toList(),
                        onBack = backToSaved,
                    )
                }
            }
        }
    }
}
