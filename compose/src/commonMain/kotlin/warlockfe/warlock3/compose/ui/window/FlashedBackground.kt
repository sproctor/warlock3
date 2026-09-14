package warlockfe.warlock3.compose.ui.window

import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.window.BackgroundFlash

/** The latest background flash asked of each window, by name; see [flashedBackground]. */
val LocalBackgroundFlashes = staticCompositionLocalOf<Map<String, BackgroundFlash>> { emptyMap() }

/**
 * The colour to paint the window named [windowName] with: its [base] background, except while a
 * flash asked of it (in [LocalBackgroundFlashes]) is playing, when it fades to the flash's colour
 * over the first half of the flash's duration and back to [base] over the second. A flash asked
 * mid-fade starts from wherever the colour is, so two in a row do not jump.
 */
@Composable
internal fun flashedBackground(
    windowName: String,
    base: Color,
): Color {
    val flash = LocalBackgroundFlashes.current[windowName]
    val animated = remember { Animatable(base, Color.VectorConverter(base.colorSpace)) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(flash) {
        if (flash == null) return@LaunchedEffect
        if (!playing) animated.snapTo(base)
        playing = true
        try {
            val half = (flash.duration / 2).inWholeMilliseconds.toInt()
            animated.animateTo(flash.color.toColor(), tween(half))
            animated.animateTo(base, tween(half))
        } finally {
            playing = false
        }
    }
    return if (playing) animated.value else base
}
