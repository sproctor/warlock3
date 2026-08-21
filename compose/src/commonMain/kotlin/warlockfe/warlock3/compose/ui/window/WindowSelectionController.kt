package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.Clipboard

/**
 * The text selection of each open window, so the `{copy}` and `{selectall}` macros can act on the
 * window the user is looking at.
 *
 * A [SelectionState] belongs to one `SelectionContainer` and is created inside the window's
 * composition, so nothing outside it can reach one. Each window view registers itself here while it
 * is composed, and the view model looks up whichever window is selected when a macro fires.
 */
class WindowSelectionController {
    private val states = mutableMapOf<String, SelectionState>()

    /**
     * The platform clipboard, supplied by the composition because it is a composition local. Text
     * goes in and out of it through `clipEntryOf` / `plainText`, which wrap what each platform
     * exposes - Compose's own text helpers for `ClipEntry` are internal to Foundation.
     */
    var clipboard: Clipboard? = null

    fun register(
        windowName: String,
        state: SelectionState,
    ) {
        states[windowName] = state
    }

    fun unregister(windowName: String) {
        states.remove(windowName)
    }

    fun stateFor(windowName: String?): SelectionState? = windowName?.let { states[it] }
}

/**
 * Provided by the game views alongside the other per-character locals, so a window view can register
 * its selection without the controller being threaded through every window layer. The default is an
 * unattached controller: nothing registers with it, so the macros find no window and do nothing.
 */
val LocalWindowSelectionController = staticCompositionLocalOf { WindowSelectionController() }
