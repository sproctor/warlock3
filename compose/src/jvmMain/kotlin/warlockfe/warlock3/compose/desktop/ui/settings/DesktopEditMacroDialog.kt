package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.util.buildKeyCombo
import warlockfe.warlock3.compose.util.buildKeyString
import warlockfe.warlock3.compose.util.getKeyModifiers
import warlockfe.warlock3.compose.util.isModifier
import warlockfe.warlock3.core.macro.Macro

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopEditMacroDialog(
    key: Key?,
    modifiers: Set<String>,
    value: String,
    saveMacro: (Macro) -> Unit,
    onClose: () -> Unit,
) {
    val newValue = rememberTextFieldState(value)
    var selectedKey by remember { mutableStateOf(key) }
    var modifierKeys by remember { mutableStateOf(modifiers) }
    val keyDisplayState = rememberTextFieldState(buildKeyString(selectedKey, modifierKeys))

    WarlockDialog(
        title = "Edit Macro",
        onCloseRequest = onClose,
        width = 480.dp,
        height = 280.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Key")
            WarlockTextField(
                state = keyDisplayState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && !event.key.isModifier()) {
                                modifierKeys = event.getKeyModifiers()
                                selectedKey = event.key
                            } else if (selectedKey == null) {
                                modifierKeys = event.getKeyModifiers()
                            }
                            keyDisplayState.edit {
                                replace(0, length, buildKeyString(selectedKey, modifierKeys))
                            }
                            true
                        },
                readOnly = true,
            )
            Text("Command")
            WarlockTextField(state = newValue, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
                    onClick = {
                        saveMacro(
                            Macro(
                                keyCombo = buildKeyCombo(selectedKey!!, modifierKeys),
                                action = newValue.text.toString(),
                            ),
                        )
                    },
                    text = "Save",
                    enabled = selectedKey != null,
                )
            }
        }
    }
}
