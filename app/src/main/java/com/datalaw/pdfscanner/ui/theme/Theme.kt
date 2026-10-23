package com.datalaw.pdfscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.datalaw.pdfscanner.settings.ThemeMode

// The palette of the old app (theme.css): warm paper in light mode, black with amber in dark mode.
private val Light = lightColorScheme(
    primary = Color(0xFF8A5A2B), onPrimary = Color(0xFFF7EEDA),
    secondaryContainer = Color(0x338A5A2B), onSecondaryContainer = Color(0xFF3B2F1E),
    background = Color(0xFFE9DCBF), onBackground = Color(0xFF3B2F1E),
    surface = Color(0xFFF1E7CE), onSurface = Color(0xFF3B2F1E),
    surfaceVariant = Color(0xFFE3D5B4), onSurfaceVariant = Color(0xFF5E4F37),
    surfaceContainer = Color(0xFFF1E7CE), surfaceContainerHigh = Color(0xFFEADFC3), surfaceContainerHighest = Color(0xFFE3D5B4),
    outline = Color(0xFFCDBB93), outlineVariant = Color(0xFFCDBB93),
    error = Color(0xFFB3261E), onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD8A25E), onPrimary = Color.Black,
    secondaryContainer = Color(0x38D8A25E), onSecondaryContainer = Color(0xFFEDE6D6),
    background = Color.Black, onBackground = Color(0xFFEDE6D6),
    surface = Color.Black, onSurface = Color(0xFFEDE6D6),
    surfaceVariant = Color(0xFF151515), onSurfaceVariant = Color(0xFFA8A190),
    surfaceContainer = Color(0xFF141414), surfaceContainerHigh = Color(0xFF1C1C1C), surfaceContainerHighest = Color(0xFF1A1A1A),
    outline = Color(0xFF3A3A3A), outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFFF6B5E), onError = Color.Black,
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Auto -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = if (dark) Dark else Light
    // The root surface sets the default text colour; without it Text is black, which is invisible on the dark background.
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.background, contentColor = scheme.onBackground, content = content)
    }
}
