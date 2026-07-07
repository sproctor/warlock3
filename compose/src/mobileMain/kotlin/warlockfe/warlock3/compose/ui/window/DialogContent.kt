package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.LocalStyleMap
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.getIgnoringCase
import kotlin.io.encoding.Base64

@Composable
fun DialogContent(
    dataObjects: List<DialogObject>,
    executeCommand: (String) -> Unit,
    style: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    // Value-bearing widgets (dropdowns, spinners) publish their current value here keyed by id; other
    // widgets' commands reference them as %<id>% (e.g. "prep %dDBSpell0%", "quickstrike %uDEQuickstrike%").
    // A spinner has no command of its own, so its value lives here until another widget consumes it.
    val values = remember { mutableStateMapOf<String, String>() }
    val execute: (String) -> Unit = { executeCommand(substitute(it, values)) }
    CompositionLocalProvider(
        LocalContentColor provides style.textColor.toColor(),
    ) {
        DialogObjectLayout(dataObjects = dataObjects, modifier = modifier) { data, skinObject ->
            when (data) {
                is DialogObject.Skin -> {
                    DialogSkin(data = data)
                }

                is DialogObject.ProgressBar -> {
                    ProgressBarWithColorMenu(
                        skinObject = skinObject,
                        data = data,
                    )
                }

                is DialogObject.Label -> {
                    Label(skinObject = skinObject, data = data)
                }

                is DialogObject.Link -> {
                    Link(
                        skinObject = skinObject,
                        data = data,
                        executeCommand = execute,
                    )
                }

                is DialogObject.Image -> {
                    DialogImage(
                        skinObject = skinObject,
                        data = data,
                        executeCommand = execute,
                        contentColor = LocalContentColor.current,
                    )
                }

                is DialogObject.Button -> {
                    val baseColor = MaterialTheme.colorScheme.primaryContainer
                    val stateLayer = MaterialTheme.colorScheme.onPrimaryContainer
                    val borderBrush = SolidColor(MaterialTheme.colorScheme.outline)
                    DialogButton(
                        onClick = { data.cmd?.let(execute) },
                        modifier = Modifier.padding(2.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        background = { isHovered, isPressed ->
                            var color = baseColor
                            if (isPressed) {
                                color = lerp(color, stateLayer, 0.10f)
                            }
                            if (isHovered) {
                                lerp(color, stateLayer, 0.08f)
                            }
                            SolidColor(color)
                        },
                        border = { _, _ -> borderBrush },
                    ) { _, _ ->
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = data.value ?: "",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }

                is DialogObject.DropDownBox -> {
                    DropDownBox(data = data, values = values, executeCommand = execute)
                }

                is DialogObject.Radio -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = data.selected,
                            onClick = { data.cmd?.let(execute) },
                        )
                        data.text?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }

                is DialogObject.UpDownEditBox -> {
                    UpDownEditBox(data = data, values = values, executeCommand = execute)
                }
            }
        }
    }
}

private val commandVariableRegex = Regex("%([^%]+)%")

// Replaces `%<id>%` placeholders in a command with the current values of the dialog's value-bearing
// widgets (e.g. "prep %dDBSpell0%" -> "prep 401"). Unknown placeholders are left as-is.
private fun substitute(
    cmd: String,
    values: Map<String, String>,
): String = commandVariableRegex.replace(cmd) { match -> values[match.groupValues[1]] ?: match.value }

@Composable
private fun DropDownBox(
    data: DialogObject.DropDownBox,
    values: SnapshotStateMap<String, String>,
    executeCommand: (String) -> Unit,
) {
    // Seed/refresh the shared value from the server; local selections below override until the next update.
    LaunchedEffect(data.id, data.value) { data.value?.let { values[data.id] = it } }
    var expanded by remember { mutableStateOf(false) }
    val selectedValue = values[data.id] ?: data.value
    val currentLabel = data.options.firstOrNull { it.value == selectedValue }?.text ?: selectedValue ?: ""
    Box(modifier = Modifier.padding(2.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(currentLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            data.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text) },
                    onClick = {
                        expanded = false
                        values[data.id] = option.value
                        data.cmd?.let(executeCommand)
                    },
                )
            }
        }
    }
}

@Composable
private fun UpDownEditBox(
    data: DialogObject.UpDownEditBox,
    values: SnapshotStateMap<String, String>,
    executeCommand: (String) -> Unit,
) {
    LaunchedEffect(data.id, data.value) { data.value?.let { values[data.id] = it.toString() } }
    val current = values[data.id]?.toIntOrNull() ?: data.value ?: data.min ?: 0

    fun step(delta: Int) {
        val next = (current + delta).coerceIn(data.min ?: Int.MIN_VALUE, data.max ?: Int.MAX_VALUE)
        if (next != current) {
            values[data.id] = next.toString()
            // A spinner usually has no command; its value is read by another widget (e.g. a button).
            data.cmd?.let(executeCommand)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "−",
            modifier = Modifier.clickable { step(-1) }.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(current.toString(), style = MaterialTheme.typography.labelSmall)
        Text(
            "+",
            modifier = Modifier.clickable { step(1) }.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private enum class ProgressBarColorTarget {
    Bar,
    Background,
    Text,
}

/**
 * A vital/progress bar that applies the current character's saved color overrides and, on long
 * press, offers a menu to recolor (or reset) the bar/background/text - the touch equivalent of the
 * desktop right-click color menu. Reads and persists through [LocalProgressBarSettings].
 */
@Composable
private fun ProgressBarWithColorMenu(
    skinObject: SkinObject?,
    data: DialogObject.ProgressBar,
) {
    val settingsState = LocalProgressBarSettings.current
    val setting = settingsState.settings[data.id]
    val barColor = setting?.barColor ?: WarlockColor.Unspecified
    val backgroundColor = setting?.backgroundColor ?: WarlockColor.Unspecified
    val textColor = setting?.textColor ?: WarlockColor.Unspecified
    // Merge the saved font override (if any) onto the base label style.
    val baseStyle = MaterialTheme.typography.labelSmall
    val style =
        baseStyle.copy(
            fontFamily = setting?.fontFamily?.let { createFontFamily(it) } ?: baseStyle.fontFamily,
            fontSize = setting?.fontSize?.sp ?: baseStyle.fontSize,
            fontWeight = setting?.fontWeight?.let { FontWeight(it) } ?: baseStyle.fontWeight,
        )

    var menuOpen by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<ProgressBarColorTarget?>(null) }
    var editingFont by remember { mutableStateOf(false) }

    Box {
        DialogProgressBar(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(data.id) {
                        detectTapGestures(onLongPress = { menuOpen = true })
                    },
            skinObject = skinObject,
            data = data,
            barColorOverride = barColor,
            backgroundColorOverride = backgroundColor,
            textColorOverride = textColor,
            style = style,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("Change bar color") },
                onClick = {
                    menuOpen = false
                    editingTarget = ProgressBarColorTarget.Bar
                },
            )
            DropdownMenuItem(
                text = { Text("Change background color") },
                onClick = {
                    menuOpen = false
                    editingTarget = ProgressBarColorTarget.Background
                },
            )
            DropdownMenuItem(
                text = { Text("Change text color") },
                onClick = {
                    menuOpen = false
                    editingTarget = ProgressBarColorTarget.Text
                },
            )
            DropdownMenuItem(
                text = { Text("Change font") },
                onClick = {
                    menuOpen = false
                    editingFont = true
                },
            )
            DropdownMenuItem(
                text = { Text("Reset colors") },
                onClick = {
                    menuOpen = false
                    settingsState.saveColors(
                        data.id,
                        WarlockColor.Unspecified,
                        WarlockColor.Unspecified,
                        WarlockColor.Unspecified,
                    )
                },
            )
        }
    }

    editingTarget?.let { target ->
        val current =
            when (target) {
                ProgressBarColorTarget.Bar -> barColor
                ProgressBarColorTarget.Background -> backgroundColor
                ProgressBarColorTarget.Text -> textColor
            }
        ColorPickerDialog(
            initialColor = current.toColor().takeIf { it.isSpecified },
            onCloseRequest = { editingTarget = null },
            onColorSelect = { chosen ->
                settingsState.saveColors(
                    data.id,
                    if (target == ProgressBarColorTarget.Bar) chosen else barColor,
                    if (target == ProgressBarColorTarget.Background) chosen else backgroundColor,
                    if (target == ProgressBarColorTarget.Text) chosen else textColor,
                )
                editingTarget = null
            },
        )
    }

    if (editingFont) {
        FontPickerDialog(
            current =
                FontConfig(
                    family = setting?.fontFamily,
                    size = setting?.fontSize,
                    weight = setting?.fontWeight,
                ),
            onCloseRequest = { editingFont = false },
            onSaveClick = { fontUpdate ->
                settingsState.saveFont(data.id, fontUpdate.fontFamily, fontUpdate.size, fontUpdate.weight)
                editingFont = false
            },
        )
    }
}

@Composable
private fun Label(
    skinObject: SkinObject?,
    data: DialogObject.Label,
) {
    val colorGroup = skinObject.getColorGroup()
    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = data.value ?: "",
            color = colorGroup.text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun Link(
    skinObject: SkinObject?,
    data: DialogObject.Link,
    executeCommand: (String) -> Unit,
) {
    // Render a dialog link as a low-emphasis text button. The label uses its skin-defined color,
    // falling back to the user-configurable "link" preset (the same one that styles stream-text
    // links), then to the Material primary color.
    val linkPreset = LocalStyleMap.current.getIgnoringCase("link")
    val presetColor = linkPreset?.textColor.toColor().takeOrElse { MaterialTheme.colorScheme.primary }
    val content = skinObject.getColorGroup().text.takeOrElse { presetColor }
    TextButton(
        modifier = Modifier.padding(horizontal = 6.dp),
        onClick = { executeCommand(data.cmd ?: "") },
    ) {
        Text(
            text = data.value ?: "",
            color = content,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun DialogSkin(data: DialogObject.Skin) {
    val skin = LocalSkin.current
    val skinObject = skin.getIgnoringCase(data.name)
    val image = skinObject?.image?.data?.let { Base64.decode(it) }
    Box {
        if (image != null) {
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = image,
                contentDescription = null,
            )
        }
    }
}
