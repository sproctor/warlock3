package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.util.buildKeyCombo
import warlockfe.warlock3.compose.util.buildKeyString
import warlockfe.warlock3.compose.util.getKeyModifiers
import warlockfe.warlock3.compose.util.isModifier
import warlockfe.warlock3.core.macro.Macro

@Composable
fun EditMacroDialog(
    key: Key?,
    modifiers: Set<String>,
    value: String,
    saveMacro: (Macro) -> Unit,
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
                    saveMacro(
                        Macro(
                            keyCombo = buildKeyCombo(selectedKey!!, modifierKeys),
                            action = newValue.text.toString(),
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        text = {
            Column {
                TextField(
                    modifier =
                        Modifier
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && !event.key.isModifier()) {
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
                    colors =
                        TextFieldDefaults.colors(
                            cursorColor = Color.Transparent,
                            errorCursorColor = Color.Transparent,
                        ),
                )
                Spacer(Modifier.height(16.dp))
                TextField(
                    state = newValue,
                    label = { Text("Command") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        },
    )
}
