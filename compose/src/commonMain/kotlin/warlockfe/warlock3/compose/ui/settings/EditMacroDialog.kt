package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toPersistentSet
import warlockfe.warlock3.compose.util.getLabel
import warlockfe.warlock3.core.macro.MacroCommand
import warlockfe.warlock3.core.macro.MacroKeyCombo

private val allModifierKeys = arrayOf(
    Key.CtrlLeft,
    Key.CtrlRight,
    Key.AltLeft,
    Key.AltRight,
    Key.ShiftLeft,
    Key.ShiftRight,
    Key.MetaLeft,
    Key.MetaRight,
)

@Composable
fun EditMacroDialog(
    key: Key?,
    modifiers: Set<String>,
    value: String,
    saveMacro: (MacroCommand) -> Unit,
    onClose: () -> Unit,
) {
    val newValue = rememberTextFieldState(value)
    var selectedKey by remember { mutableStateOf(key) }
    var modifierKeys by remember { mutableStateOf(modifiers) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Macro") },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedKey != null,
                onClick = {
                    if (selectedKey != null) {
                        saveMacro(
                            MacroCommand(
                                keyCombo = buildKeyCombo(selectedKey!!, modifierKeys),
                                command = newValue.text.toString()
                            )
                        )
                    }
                }
            ) {
                Text("OK")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .scrollable(
                        state = rememberScrollState(),
                        orientation = Orientation.Horizontal
                    )
            ) {
                TextField(
                    modifier = Modifier
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && !allModifierKeys.contains(event.key)) {
                                modifierKeys = event.getKeyModifiers()
                                selectedKey = event.key
                            } else if (selectedKey == null) {
                                modifierKeys = event.getKeyModifiers()
                            }
                            true
                        },
                    value = TextFieldValue(text = buildKeyString(selectedKey, modifierKeys)),
                    label = { Text("Key") },
                    onValueChange = {},
                    maxLines = 1,
                    colors = TextFieldDefaults.colors(
                        cursorColor = Color.Transparent,
                        errorCursorColor = Color.Transparent
                    ),
                )
                Spacer(Modifier.height(16.dp))
                TextField(
                    state = newValue,
                    label = { Text("Command") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        }
    )
}

private fun buildKeyString(key: Key?, modifierKeys: Set<String>): String {
    val newKey = StringBuilder()
    if (modifierKeys.contains("ctrl")) newKey.append("ctrl+")
    if (modifierKeys.contains("alt")) newKey.append("alt+")
    if (modifierKeys.contains("shift")) newKey.append("shift+")
    if (modifierKeys.contains("meta")) newKey.append("meta+")
    if (key != null) {
        newKey.append(key.getLabel())
    }
    return newKey.toString()
}

private fun KeyEvent.getKeyModifiers(): Set<String> {
    val modifiers = mutableSetOf<String>()
    if (isAltPressed) modifiers.add("alt")
    if (isShiftPressed) modifiers.add("shift")
    if (isMetaPressed) modifiers.add("meta")
    if (isCtrlPressed) modifiers.add("ctrl")
    return modifiers.toPersistentSet()
}

private fun buildKeyCombo(key: Key, modifierKeys: Set<String>): MacroKeyCombo {
    return MacroKeyCombo(
        keyCode = key.keyCode,
        ctrl = modifierKeys.contains("ctrl"),
        alt = modifierKeys.contains("alt"),
        shift = modifierKeys.contains("shift"),
        meta = modifierKeys.contains("meta"),
    )
}
