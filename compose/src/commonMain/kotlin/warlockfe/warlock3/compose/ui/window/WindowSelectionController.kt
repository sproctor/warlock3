package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString

/**
 * What a window exposes of its text selection: enough for the `{copy}` and `{selectall}` macros.
 */
interface WindowSelection {
    /** The selected text, one entry per selected row, as Compose's own copy would join them. */
    val selectedTexts: List<AnnotatedString>

    /** Selects every composed row - which is all a lazy list offers. */
    fun selectAll()
}

/**
 * The text selection of each open window, so the `{copy}` and `{selectall}` macros can act on the
 * window the user is looking at.
 *
 * A selection belongs to one `SelectionContainer` and is created inside the window's composition,
 * so nothing outside it can reach one. Each window view registers itself here while it is composed,
 * and the view model looks up whichever window is selected when a macro fires.
 */
class WindowSelectionController {
    private val selections = mutableMapOf<String, WindowSelection>()

    /**
     * The platform clipboard, supplied by the composition because it is a composition local. Text
     * goes in and out of it through `clipEntryOf` / `plainText`, which wrap what each platform
     * exposes - Compose's own text helpers for `ClipEntry` are internal to Foundation.
     */
    var clipboard: Clipboard? = null

    fun register(
        windowName: String,
        selection: WindowSelection,
    ) {
        selections[windowName] = selection
    }

    fun unregister(windowName: String) {
        selections.remove(windowName)
    }

    fun selectionFor(windowName: String?): WindowSelection? = windowName?.let { selections[it] }
}

/**
 * Provided by the game views alongside the other per-character locals, so a window view can register
 * its selection without the controller being threaded through every window layer. The default is an
 * unattached controller: nothing registers with it, so the macros find no window and do nothing.
 */
val LocalWindowSelectionController = staticCompositionLocalOf { WindowSelectionController() }
