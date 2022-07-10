package cc.warlock.warlock3.app

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val warlockPurple = Color(0xFF6804f3)

val Colors.onPrimarySurface: Color
    get() = if (isLight) onPrimary else onSurface

val WarlockIcons = Icons.Filled

// TODO: implement a decent dark colors theme
val warlockColorsDark = lightColors(
    primary = warlockPurple,
//    background = Color(0xFF222222),
//    surface = Color(0xFF222222),
    //error = Color(0xFFCF6679),
    onPrimary = Color.White,
    //onSecondary = Color.Black,
    //onBackground = Color.White,
    //onSurface = Color.White,
    //onError = Color.Black
)

@Composable
fun WarlockTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = warlockColorsDark,
        content = content
    )
}