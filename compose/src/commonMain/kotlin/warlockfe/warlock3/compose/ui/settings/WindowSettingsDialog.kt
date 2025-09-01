package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.ColorPickerButton
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.FontUpdate
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.ifUnspecified

@Composable
fun WindowSettingsDialog(
    onCloseRequest: () -> Unit,
    style: StyleDefinition,
    defaultStyle: StyleDefinition,
    saveStyle: (StyleDefinition) -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }
    var editFont by remember { mutableStateOf<Pair<StyleDefinition, (FontUpdate) -> Unit>?>(null) }

    if (editColor != null) {
        ColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelected = { color ->
                editColor?.second?.invoke(color)
                editColor = null
            }
        )
    }
    if (editFont != null) {
        FontPickerDialog(
            currentStyle = editFont!!.first,
            onCloseRequest = { editFont = null },
            onSaveClicked = { fontUpdate ->
                editFont?.second?.invoke(fontUpdate)
                editFont = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onCloseRequest,
        confirmButton = {
            TextButton(onClick = onCloseRequest) {
                Text("Close")
            }
        },
        text = {
            Column(Modifier.padding(24.dp)) {
                val textColor = style.textColor.ifUnspecified(defaultStyle.textColor)
                ColorPickerButton(
                    text = "Content",
                    color = textColor.toColor(),
                    onClick = {
                        editColor = Pair(textColor) { color ->
                            saveStyle(style.copy(textColor = color))
                        }
                    }
                )
                Spacer(Modifier.width(16.dp))
                val backgroundColor = style.backgroundColor.ifUnspecified(defaultStyle.backgroundColor)
                ColorPickerButton(
                    text = "Background",
                    color = backgroundColor.toColor(),
                    onClick = {
                        editColor = Pair(backgroundColor) { color ->
                            saveStyle(
                                style.copy(backgroundColor = color)
                            )
                        }
                    }
                )
                Spacer(Modifier.width(16.dp))
                OutlinedButton(
                    onClick = {
                        editFont = Pair(style) { fontUpdate ->
                            saveStyle(style.copy(fontFamily = fontUpdate.fontFamily, fontSize = fontUpdate.size))
                        }
                    }
                ) {
                    Text("Font: ${style.fontFamily ?: "Default"} ${style.fontSize ?: "Default"}")
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = {
                    saveStyle(StyleDefinition())
                }) {
                    Text("Revert to defaults")
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    )
}

