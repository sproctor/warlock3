package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

@Stable
fun Modifier.mirror(): Modifier = scale(scaleX = -1f, scaleY = 1f)

inline fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier.() -> Modifier,
): Modifier = conditional(condition = condition, ifTrue = ifTrue, ifFalse = { this })

inline fun Modifier.conditional(
    condition: Boolean,
    ifTrue: Modifier.() -> Modifier,
    ifFalse: Modifier.() -> Modifier,
): Modifier {
    return if (condition) {
        ifTrue()
    } else {
        ifFalse()
    }
}