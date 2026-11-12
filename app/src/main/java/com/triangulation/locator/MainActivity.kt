package com.triangulation.locator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.triangulation.locator.data.ObservationStore
import com.triangulation.locator.ui.CaptureScreen
import com.triangulation.locator.ui.MapScreen
import com.triangulation.locator.ui.SensorTestScreen
import com.triangulation.locator.ui.SessionScreen
import com.triangulation.locator.ui.SessionsScreen
import com.triangulation.locator.ui.TypedBearingScreen
import com.triangulation.locator.ui.theme.TriangulationTheme

private enum class Screen { Capture, Sensors, History, Session, Map, TypedBearing }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriangulationTheme {
                val context = LocalContext.current
                val store = remember { ObservationStore(context.applicationContext) }
                var screen by rememberSaveable { mutableStateOf(Screen.Capture) }
                // The session being looked at (Session and Map screens). Saved, so it survives
                // Android closing the app in the background.
                var sessionId by rememberSaveable { mutableLongStateOf(0L) }
                // Where Back from a session goes: History, or Capture if opened from there.
                var sessionOpenedFrom by rememberSaveable { mutableStateOf(Screen.History) }

                val backToCapture = { screen = Screen.Capture }
                fun openSession(id: Long, from: Screen) {
                    sessionId = id
                    sessionOpenedFrom = from
                    screen = Screen.Session
                }

                when (screen) {
                    Screen.Capture -> Unit
                    Screen.Map, Screen.TypedBearing -> BackHandler { screen = Screen.Session }
                    Screen.Session -> BackHandler { screen = sessionOpenedFrom }
                    else -> BackHandler(onBack = backToCapture)
                }

                when (screen) {
                    Screen.Capture -> CaptureScreen(
                        store = store,
                        onOpenSensors = { screen = Screen.Sensors },
                        onOpenHistory = { screen = Screen.History },
                        onOpenSession = { openSession(it, Screen.Capture) },
                    )
                    Screen.Sensors -> SensorTestScreen(onBack = backToCapture)
                    Screen.History -> SessionsScreen(
                        store = store,
                        onOpenSession = { openSession(it, Screen.History) },
                        onBack = backToCapture,
                    )
                    Screen.Session -> SessionScreen(
                        store = store,
                        sessionId = sessionId,
                        onShowMap = { screen = Screen.Map },
                        onAddTyped = { screen = Screen.TypedBearing },
                        onBack = { screen = sessionOpenedFrom },
                    )
                    Screen.TypedBearing -> TypedBearingScreen(
                        store = store,
                        sessionId = sessionId,
                        onDone = { screen = Screen.Session },
                    )
                    Screen.Map -> MapScreen(
                        store = store,
                        sessionId = sessionId,
                        onBack = { screen = Screen.Session },
                    )
                }
            }
        }
    }
}
