package com.opensubsonic.client.ui.theme

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
    primary = Black,
    onPrimary = White,
    primaryContainer = LightGray,
    onPrimaryContainer = Black,
    secondary = Red,
    onSecondary = White,
    secondaryContainer = Red.copy(alpha = 0.12f),
    onSecondaryContainer = DarkRed,
    tertiary = MediumGray,
    onTertiary = White,
    background = White,
    onBackground = Black,
    surface = SurfaceLight,
    onSurface = Black,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = DarkGray,
    outline = LightGray,
    outlineVariant = LightGray,
    error = Red,
    onError = White
)

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = DarkGray,
    onPrimaryContainer = White,
    secondary = Red,
    onSecondary = White,
    secondaryContainer = Red.copy(alpha = 0.2f),
    onSecondaryContainer = Red,
    tertiary = MediumGray,
    onTertiary = Black,
    background = Black,
    onBackground = White,
    surface = SurfaceDark,
    onSurface = White,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = LightGray,
    outline = DarkGray,
    outlineVariant = DarkGray,
    error = Red,
    onError = White
)

@Composable
fun SubTuneTheme(
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
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SubTuneTypography,
        content = content
    )
}
