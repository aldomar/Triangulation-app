package com.triangulation.locator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.triangulation.locator.ui.SensorTestScreen
import com.triangulation.locator.ui.theme.TriangulationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriangulationTheme {
                SensorTestScreen()
            }
        }
    }
}
