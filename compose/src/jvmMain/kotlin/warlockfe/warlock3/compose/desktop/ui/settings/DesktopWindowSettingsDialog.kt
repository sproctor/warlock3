package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.FontUpdate
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerButton
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerDialog
import warlockfe.warlock3.compose.desktop.components.DesktopFontPickerDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.ifUnspecified

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopWindowSettingsDialog(
    onCloseRequest: () -> Unit,
    style: StyleDefinition,
    defaultStyle: StyleDefinition,
    saveStyle: (StyleDefinition) -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }
    var editFont by remember { mutableStateOf<Pair<StyleDefinition, (FontUpdate) -> Unit>?>(null) }

    if (editColor != null) {
        DesktopColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                editColor?.second?.invoke(color)
                editColor = null
            },
        )
    }
    if (editFont != null) {
        DesktopFontPickerDialog(
            currentStyle = editFont!!.first,
            onCloseRequest = { editFont = null },
            onSaveClick = { fontUpdate ->
                editFont?.second?.invoke(fontUpdate)
                editFont = null
            },
        )
    }

    WarlockDialog(
        title = "Window settings",
        onCloseRequest = onCloseRequest,
        width = 480.dp,
        height = 360.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val textColor = style.textColor.ifUnspecified(defaultStyle.textColor)
            DesktopColorPickerButton(
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
            DesktopColorPickerButton(
                text = "Background",
                color = backgroundColor.toColor(),
                onClick = {
                    editColor =
                        Pair(backgroundColor) { color ->
                            saveStyle(style.copy(backgroundColor = color))
                        }
                },
            )
            WarlockOutlinedButton(
                onClick = {
                    editFont =
                        Pair(style) { fontUpdate ->
                            saveStyle(
                                style.copy(
                                    fontFamily = fontUpdate.fontFamily,
                                    fontSize = fontUpdate.size,
                                ),
                            )
                        }
                },
                text = "Font: ${style.fontFamily ?: "Default"} ${style.fontSize ?: "Default"}",
            )
            WarlockButton(
                onClick = { saveStyle(StyleDefinition()) },
                text = "Revert to defaults",
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockButton(onClick = onCloseRequest, text = "Close")
            }
        }
    }
}
