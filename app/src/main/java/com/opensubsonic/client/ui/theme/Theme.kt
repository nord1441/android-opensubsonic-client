package com.opensubsonic.client.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TermCyan,
    onPrimary = TermBlack,
    primaryContainer = TermSurfaceDark,
    onPrimaryContainer = TermCyan,
    secondary = TermGreen,
    onSecondary = TermBlack,
    secondaryContainer = TermSurfaceDark,
    onSecondaryContainer = TermGreen,
    tertiary = TermMagenta,
    onTertiary = TermBlack,
    background = TermBlack,
    onBackground = TermFg,
    surface = TermDarkBg,
    onSurface = TermFg,
    surfaceVariant = TermSurfaceDark,
    onSurfaceVariant = TermSubtle,
    outline = TermBorder,
    outlineVariant = TermBorder,
    error = TermRed,
    onError = TermBlack
)

private val LightColorScheme = lightColorScheme(
    primary = LightCyan,
    onPrimary = LightBg,
    primaryContainer = LightSurfaceVar,
    onPrimaryContainer = LightCyan,
    secondary = LightGreen,
    onSecondary = LightBg,
    secondaryContainer = LightSurfaceVar,
    onSecondaryContainer = LightGreen,
    tertiary = LightMuted,
    onTertiary = LightBg,
    background = LightBg,
    onBackground = LightFg,
    surface = LightSurface,
    onSurface = LightFg,
    surfaceVariant = LightSurfaceVar,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = LightRed,
    onError = LightBg
)

// Sharp rectangular shapes - no rounded corners (tiling WM aesthetic)
private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
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
        shapes = TerminalShapes,
        content = content
    )
}
