package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.WarlockAction

// The parts of a panel's widgets that are the same on every platform: what pressing a button sends,
// and how an edit box's text reaches the values other widgets' commands read. Each platform only
// draws them.

private val commandVariableRegex = Regex("%([^%]+)%")

// Replaces `%<id>%` placeholders in a command with the current values of the panel's value-bearing
// widgets (e.g. "prep %dDBSpell0%" -> "prep 401"). Unknown placeholders are left as-is.
internal fun substitute(
    cmd: String,
    values: Map<String, String>,
): String = commandVariableRegex.replace(cmd) { match -> values[match.groupValues[1]] ?: match.value }

/**
 * What a widget's command sends, with `%<id>%` filled in. The echo is substituted too: the BUG
 * dialog echoes "bug %category%;%title%;%details%", and Saga shows the filled-in line, not the
 * template.
 */
internal fun widgetCommand(
    cmd: String,
    echo: String?,
    values: Map<String, String>,
): WarlockAction = WarlockAction.SendWidgetCommand(substitute(cmd, values), echo?.let { substitute(it, values) })

/**
 * What pressing [button] in the panel [panelId] sends, or null for a button with nothing to send.
 *
 * A close button sends its command first and dismisses the panel after, which is the order the real
 * client uses. It goes as one action so the two halves cannot be reordered on their way to the
 * socket. [panelId] is null for chrome, which has no panel for a close button to dismiss.
 */
internal fun buttonAction(
    button: PanelObject.Button,
    panelId: String?,
    values: Map<String, String>,
): WarlockAction? {
    val closes = panelId?.takeIf { button.closesPanel }
    return if (closes != null) {
        WarlockAction.ClosePanel(
            id = closes,
            command = button.cmd?.let { substitute(it, values) },
            echo = button.echo?.let { substitute(it, values) },
        )
    } else {
        button.cmd?.let { widgetCommand(it, button.echo, values) }
    }
}

/** The button Enter in [editBox] presses: the one its `enterButton` names, if the panel has it. */
internal fun enterButtonOf(
    editBox: PanelObject.EditBox,
    dataObjects: List<PanelObject>,
): PanelObject.Button? = editBox.enterButton?.let { id -> dataObjects.firstOrNull { it.id == id } as? PanelObject.Button }

/**
 * The text of [data], seeded and refreshed from the server, and published to [values] as it is
 * typed, so another widget's `%<id>%` reads it. Typing overrides the server's value until the next
 * update, as for the other widgets.
 */
@Composable
internal fun rememberEditBoxState(
    data: PanelObject.EditBox,
    values: SnapshotStateMap<String, String>,
): TextFieldState {
    val state = rememberTextFieldState(data.value ?: "")
    LaunchedEffect(data.id, data.value) { data.value?.let { state.setTextAndPlaceCursorAtEnd(it) } }
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { values[data.id] = it } }
    return state
}
