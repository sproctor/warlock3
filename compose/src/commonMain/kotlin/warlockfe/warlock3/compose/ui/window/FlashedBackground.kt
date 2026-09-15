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
import kotlinx.coroutines.delay
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.window.BackgroundFlash
import kotlin.time.Duration

/** The latest background flash asked of each window, by name; see [flashedBackground]. */
val LocalBackgroundFlashes = staticCompositionLocalOf<Map<String, BackgroundFlash>> { emptyMap() }

/**
 * The colour to paint the window named [windowName] with: its [base] background, except while a
 * flash asked of it (in [LocalBackgroundFlashes]) is playing, when it goes to the flash's colour
 * over the fade-in, holds it, and comes back to [base] over the fade-out; a fade of zero is a
 * cut. A flash asked mid-play starts from wherever the colour is, so two in a row do not jump.
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
        animated.go(flash.color.toColor(), flash.fadeIn)
        delay(flash.hold)
        animated.go(base, flash.fadeOut)
        // Only a flash that ran to its end gives the window back. A replacement cancels this
        // effect before its own runs, so clearing on cancellation would have the replacement
        // find nothing playing and snap to the base colour instead of going on from this one.
        playing = false
    }
    return if (playing) animated.value else base
}

private suspend fun Animatable<Color, *>.go(
    target: Color,
    over: Duration,
) {
    if (over <= Duration.ZERO) {
        snapTo(target)
    } else {
        // A tween takes an Int of milliseconds; a script may ask for longer than one holds.
        animateTo(target, tween(over.inWholeMilliseconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
    }
}
