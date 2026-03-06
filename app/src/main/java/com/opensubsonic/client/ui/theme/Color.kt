package com.opensubsonic.client.ui.theme

import androidx.compose.ui.graphics.Color

// Terminal / Sway WM inspired palette
val TermBlack = Color(0xFF1a1b26)       // Deep dark background (Tokyo Night-ish)
val TermDarkBg = Color(0xFF24283b)      // Slightly lighter bg for surfaces
val TermSurfaceDark = Color(0xFF2f3549) // Surface variant
val TermBorder = Color(0xFF3b4261)      // Borders and outlines
val TermMuted = Color(0xFF565f89)       // Muted text / inactive
val TermSubtle = Color(0xFF787c99)      // Subtle text
val TermFg = Color(0xFFa9b1d6)         // Primary foreground text
val TermBright = Color(0xFFc0caf5)     // Bright foreground

// Accent colors
val TermRed = Color(0xFFf7768e)         // Primary accent (active/selected)
val TermRedDim = Color(0xFF914455)      // Dim red (inactive/unselected buttons)
val TermRedContainer = Color(0xFF3d1f28) // Dark red container bg
val TermCyan = Color(0xFF7dcfff)        // Info accent
val TermGreen = Color(0xFF9ece6a)       // Success
val TermYellow = Color(0xFFe0af68)      // Warning
val TermMagenta = Color(0xFFbb9af7)     // Tertiary accent
val TermOrange = Color(0xFFff9e64)      // Secondary warm accent

// Light theme (high contrast terminal)
val LightBg = Color(0xFFf0f0f0)         // Light bg like a light terminal
val LightSurface = Color(0xFFe8e8e8)    // Surface
val LightSurfaceVar = Color(0xFFdcdcdc) // Surface variant
val LightBorder = Color(0xFFbebebe)     // Borders
val LightFg = Color(0xFF1a1b26)         // Dark text on light
val LightMuted = Color(0xFF5c6370)      // Muted text
val LightRed = Color(0xFFc62828)        // Accent on light (active)
val LightRedDim = Color(0xFF8c1c1c)     // Dim red on light (inactive)
val LightRedContainer = Color(0xFFf5d5d5) // Light red container
