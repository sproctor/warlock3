package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.CheckboxRow
import warlockfe.warlock3.compose.components.ColorPickerButton
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.ifUnspecified

@Composable
fun WindowSettingsDialog(
    onCloseRequest: () -> Unit,
    style: StyleDefinition,
    defaultStyle: StyleDefinition,
    saveStyle: (StyleDefinition) -> Unit,
    font: FontConfig? = null,
    monoFont: FontConfig? = null,
    saveFont: (FontConfig?) -> Unit = {},
    saveMonoFont: (FontConfig?) -> Unit = {},
    showFontOptions: Boolean = true,
    nameFilterOption: Boolean = false,
    nameFilter: Boolean = false,
    saveNameFilter: (Boolean) -> Unit = {},
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }
    // (current font, monospaceOnly). On save the flag decides which override is written.
    var editFont by remember { mutableStateOf<Pair<FontConfig?, Boolean>?>(null) }

    if (editColor != null) {
        ColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                editColor?.second?.invoke(color)
                editColor = null
            },
        )
    }
    editFont?.let { (current, monospaceOnly) ->
        FontPickerDialog(
            current = current,
            monospaceOnly = monospaceOnly,
            onCloseRequest = { editFont = null },
            onSaveClick = { fontUpdate ->
                if (monospaceOnly) saveMonoFont(fontUpdate.toFontConfig()) else saveFont(fontUpdate.toFontConfig())
                editFont = null
            },
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val textColor = style.textColor.ifUnspecified(defaultStyle.textColor)
                ColorPickerButton(
                    text = "Content",
                    color = textColor.toColor(),
                    onClick = {
                        editColor =
                            Pair(textColor) { color ->
                                saveStyle(style.copy(textColor = color))
                            }
                    },
                )
                val backgroundColor = style.backgroundColor.ifUnspecified(defaultStyle.backgroundColor)
                ColorPickerButton(
                    text = "Background",
                    color = backgroundColor.toColor(),
                    onClick = {
                        editColor =
                            Pair(backgroundColor) { color ->
                                saveStyle(
                                    style.copy(backgroundColor = color),
                                )
                            }
                    },
                )
                if (showFontOptions) {
                    OutlinedButton(onClick = { editFont = font to false }) {
                        Text("Font: ${font.fontLabel()}")
                    }
                    OutlinedButton(onClick = { editFont = monoFont to true }) {
                        Text("Monospace font: ${monoFont.fontLabel()}")
                    }
                }
                if (nameFilterOption) {
                    CheckboxRow(
                        checked = nameFilter,
                        onCheckedChange = { saveNameFilter(it) },
                        text = "Only show lines with names in list",
                    )
                }
                Button(onClick = {
                    saveStyle(StyleDefinition())
                    saveFont(null)
                    saveMonoFont(null)
                }) {
                    Text("Revert to defaults")
                }
            }
        },
    )
}
