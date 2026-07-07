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
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerButton
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerDialog
import warlockfe.warlock3.compose.desktop.components.DesktopFontPickerDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.FontConfig
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
        DesktopColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                editColor?.second?.invoke(color)
                editColor = null
            },
        )
    }
    editFont?.let { (current, monospaceOnly) ->
        DesktopFontPickerDialog(
            current = current,
            monospaceOnly = monospaceOnly,
            onCloseRequest = { editFont = null },
            onSaveClick = { fontUpdate ->
                if (monospaceOnly) saveMonoFont(fontUpdate.toFontConfig()) else saveFont(fontUpdate.toFontConfig())
                editFont = null
            },
        )
    }

    WarlockDialog(
        title = "Window settings",
        onCloseRequest = onCloseRequest,
        width = 480.dp,
        height = 400.dp,
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
            if (showFontOptions) {
                WarlockOutlinedButton(
                    onClick = { editFont = font to false },
                    text = "Font: ${font.fontLabel()}",
                )
                WarlockOutlinedButton(
                    onClick = { editFont = monoFont to true },
                    text = "Monospace font: ${monoFont.fontLabel()}",
                )
            }
            if (nameFilterOption) {
                WarlockCheckboxRow(
                    checked = nameFilter,
                    onCheckedChange = { saveNameFilter(it) },
                    text = "Only show lines with names in list",
                )
            }
            WarlockButton(
                onClick = {
                    saveStyle(StyleDefinition())
                    saveFont(null)
                    saveMonoFont(null)
                },
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
