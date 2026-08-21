package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.type

/**
 * The game screen's `onPreviewKeyEvent` handler: macros first, then anything left over goes to the
 * command entry, which [entryFocusRequester] focuses so the keystroke lands in it.
 *
 * Returns one stable lambda per [viewModel] rather than a new one each composition, so a keystroke
 * in the entry - which recomposes the whole screen - does not reinstall the key modifier on the
 * root of it.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
internal fun rememberGameKeyHandler(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
): (KeyEvent) -> Boolean {
    // On JVM, KeyDown is followed by an Unknown/KEY_TYPED event
    val ignoreNextUnknownKeyEvent = remember { mutableStateOf(false) }
    return remember(viewModel, entryFocusRequester) {
        { event: KeyEvent ->
            if (ignoreNextUnknownKeyEvent.value && event.type == KeyEventType.Unknown) {
                ignoreNextUnknownKeyEvent.value = false
                true
            } else {
                ignoreNextUnknownKeyEvent.value = false
                // The find bar handles its own keys while it's focused; step aside so it can.
                if (viewModel.windowFindController.focused.value) {
                    false
                } else {
                    viewModel.handleKeyPress(event).also {
                        ignoreNextUnknownKeyEvent.value = it
                        if (!it &&
                            event.type == KeyEventType.KeyDown &&
                            !event.isAltPressed &&
                            !event.isCtrlPressed &&
                            !event.isMetaPressed
                        ) {
                            entryFocusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}
