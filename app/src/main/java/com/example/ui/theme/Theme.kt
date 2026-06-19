package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    background = BackgroundLightColor,
    surface = SurfaceLightColor,
    surfaceVariant = SurfaceVariantLightColor,
    onBackground = OnSurfaceLightColor,
    onSurface = OnSurfaceLightColor,
    outline = OutlineColor
)

private val DarkColorScheme = darkColorScheme(
    primary = SecondaryBlue,
    onPrimary = OnPrimaryContainerLight,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = SecondaryContainerLight,
    secondary = SecondaryBlue,
    onSecondary = OnPrimaryContainerLight,
    secondaryContainer = TerminalBackground,
    onSecondaryContainer = TerminalTextWhite,
    background = TerminalBackground,
    surface = TerminalBackground,
    onBackground = TerminalTextWhite,
    onSurface = TerminalTextWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = !darkTheme
            windowInsetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
