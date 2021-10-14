package cc.warlock.warlock3.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val warlockPurple = Color(0xFF6804f3)

private val warlockColorsLight = lightColors(
    primary = warlockPurple
)

private val warlockColorsDark = darkColors(
    primary = warlockPurple
)

@Composable
fun WarlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = if (darkTheme) warlockColorsDark else warlockColorsLight,
        content = content
    )
}

val Colors.onPrimarySurface: Color
    get() = if (isLight) onPrimary else onSurface

val WarlockIcons = Icons.Filled