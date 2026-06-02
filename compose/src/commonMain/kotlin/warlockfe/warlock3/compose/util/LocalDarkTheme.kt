package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the app is currently rendering in dark mode. Provided by the theme wrappers
 * (WarlockDesktopTheme / AppTheme) and used to resolve per-mode skin colors. Defaults to light.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }
