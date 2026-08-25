package warlockfe.warlock3.compose.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Runs [onEvent] for every pointer event on [pass], consuming nothing: for the observers around the
 * window views that watch a gesture without taking part in it. The pass is required rather than
 * defaulted because the observers differ in it, and the difference is behavior, not style: Initial
 * sees the event before any handler, Main after children but before ancestors, Final after everyone
 * - which is also the only pass that can see whether the event was consumed.
 */
internal fun Modifier.onEachPointerEvent(
    pass: PointerEventPass,
    key: Any? = Unit,
    onEvent: (PointerEvent) -> Unit,
): Modifier =
    pointerInput(key, pass) {
        awaitPointerEventScope {
            while (true) {
                onEvent(awaitPointerEvent(pass))
            }
        }
    }

/** [onEachPointerEvent], filtered to presses. */
internal fun Modifier.onPress(
    pass: PointerEventPass,
    key: Any? = Unit,
    onPress: (PointerEvent) -> Unit,
): Modifier =
    onEachPointerEvent(pass, key) { event ->
        if (event.type == PointerEventType.Press) {
            onPress(event)
        }
    }
