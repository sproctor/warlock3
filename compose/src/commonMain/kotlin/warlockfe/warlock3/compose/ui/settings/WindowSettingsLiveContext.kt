package warlockfe.warlock3.compose.ui.settings

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.window.WindowInfo

/**
 * The live connection's window state, handed to the Appearance -> Windows settings section so it can
 * show current window titles and toggle windows in the running layout. Non-null only while a character
 * is connected; the section falls back to id-only labels and repository-only show/hide when it's null
 * or when the character being edited isn't the connected one.
 *
 * A plain data class (rather than the GameViewModel itself) keeps the settings tree decoupled from the
 * VM, and [windowInfo] is a flow so the settings host doesn't recompose on every window-info tick.
 */
data class WindowSettingsLiveContext(
    val connectedCharacterId: String,
    val windowInfo: StateFlow<List<WindowInfo>>,
    val openWindow: (String) -> Unit,
    val closeWindow: (String) -> Unit,
)
