package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor

@Composable
actual fun WindowSettingsDialog(
    onCloseRequest: () -> Unit,
    style: StyleDefinition,
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

    DialogWindow(
        title = "Window Settings",
        onCloseRequest = onCloseRequest
    ) {
        Column {
            OutlinedButton(
                onClick = {
                    editColor = Pair(style.textColor) { color ->
                        saveStyle(
                            style.copy(textColor = color)
                        )
                    }
                }
            ) {
                Row {
                    Text("Content: ")
                    Box(
                        Modifier.size(16.dp).background(style.textColor.toColor()).border(1.dp, Color.Black)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            OutlinedButton(
                onClick = {
                    editColor = Pair(style.backgroundColor) { color ->
                        saveStyle(
                            style.copy(backgroundColor = color)
                        )
                    }
                }
            ) {
                Row {
                    Text("Background: ")
                    Box(
                        Modifier.size(16.dp).background(style.backgroundColor.toColor())
                            .border(1.dp, Color.Black)
                    )
                }
            }
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
}
