package com.musicplayer.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SoftCream, // Header text and primary elements
    onPrimary = Black,
    secondary = SoftCream,
    onSecondary = Black,
    tertiary = MutedRose,
    background = Black,
    onBackground = SoftCream,
    surface = Black,
    onSurface = SoftCream,
    surfaceVariant = DarkGrey,
    onSurfaceVariant = MutedCream,
    // Bottom bar selection pill and other container-based highlights
    secondaryContainer = DustyRose,
    onSecondaryContainer = Black,
    // Selection highlight for NavigationBar
    primaryContainer = DustyRose,
    onPrimaryContainer = Black
)

@Composable
fun MusicPlayerTheme(
    fontWeightScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = getTypography(fontWeightScale),
        content = content
    )
}
