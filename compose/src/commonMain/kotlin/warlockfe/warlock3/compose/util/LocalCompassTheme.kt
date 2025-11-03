package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import warlockfe.warlock3.compose.components.CompassTheme

val LocalCompassTheme = staticCompositionLocalOf<CompassTheme> { error("No compass theme provided") }