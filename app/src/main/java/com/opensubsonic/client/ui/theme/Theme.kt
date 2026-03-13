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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkRed,
    onPrimary = DarkBlack,
    primaryContainer = DarkRedContainer,
    onPrimaryContainer = DarkRed,
    secondary = DarkRedDim,
    onSecondary = DarkFg,
    secondaryContainer = DarkRedContainer,
    onSecondaryContainer = DarkRedDim,
    tertiary = DarkMagenta,
    onTertiary = DarkBlack,
    background = DarkBlack,
    onBackground = DarkFg,
    surface = DarkBg,
    onSurface = DarkFg,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkSubtle,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = DarkOrange,
    onError = DarkBlack
)

private val LightColorScheme = lightColorScheme(
    primary = LightRed,
    onPrimary = LightBg,
    primaryContainer = LightRedContainer,
    onPrimaryContainer = LightRed,
    secondary = LightRedDim,
    onSecondary = LightBg,
    secondaryContainer = LightRedContainer,
    onSecondaryContainer = LightRedDim,
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
    error = Color(0xFFd32f2f),
    onError = LightBg
)

// Swiss Modern shapes — clean with subtle rounding
private val SwissShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
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
        shapes = SwissShapes,
        content = content
    )
}
