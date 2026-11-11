package com.triangulation.locator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFFFF7A3D)
private val Slate = Color(0xFF1F3A4D)

private val LightColors = lightColorScheme(
    primary = Slate,
    secondary = Accent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7E4),
    secondary = Accent,
)

/** Status colours used for accuracy badges. Readable in both light and dark mode. */
object StatusColors {
    val Good = Color(0xFF2E9D57)
    val Fair = Color(0xFFD99A00)
    val Poor = Color(0xFFE0682B)
    val Bad = Color(0xFFD33A3A)
}

@Composable
fun TriangulationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
