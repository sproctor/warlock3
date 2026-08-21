package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.DEFAULT_PANEL_SCALE
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.PANEL_BASE_FONT_SIZE
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalPanelDefaults
import warlockfe.warlock3.compose.util.LocalPanelTextStyle
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.LocalStyleMap
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.progressBarSkinFont
import warlockfe.warlock3.compose.util.toAlignment
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.compose.util.withFont
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.getIgnoringCase
import kotlin.io.encoding.Base64

// The compact base every panel widget draws with before the panel font is merged over it.
private val panelBaseStyle
    @Composable
    get() =
        MaterialTheme.typography.labelSmall.copy(
            fontSize = PANEL_BASE_FONT_SIZE.sp,
        )

@Composable
fun PanelContent(
    dataObjects: List<PanelObject>,
    // The panel this content belongs to, or null when it is drawn as chrome (the status bar's
    // vitals) rather than as a window - chrome has no panel for a close button to dismiss.
    panelId: String?,
    onAction: (WarlockAction) -> Unit,
    style: StyleDefinition,
    modifier: Modifier = Modifier,
    // Per-window overrides of the character's panel font and scale; null falls back to the character's.
    font: FontConfig? = null,
    scale: Float? = null,
) {
    // Value-bearing widgets (dropdowns, spinners) publish their current value here keyed by id; other
    // widgets' commands reference them as %<id>% (e.g. "prep %dDBSpell0%", "quickstrike %uDEQuickstrike%").
    // A spinner has no command of its own, so its value lives here until another widget consumes it.
    val values = remember { mutableStateMapOf<String, String>() }
    // A checkbox's tick, kept apart from its substitution value above: nothing stops a box from
    // carrying the same string for both states, and reading the tick back off [values] would then
    // leave it permanently ticked and impossible to clear.
    val checkedStates = remember { mutableStateMapOf<String, Boolean>() }
    val executeWidget: (String, String?) -> Unit = { cmd, echo ->
        onAction(WarlockAction.SendWidgetCommand(substitute(cmd, values), echo))
    }
    val execute: (String) -> Unit = { executeWidget(it, null) }
    // Wrayth gives each widget type its own font out of the skin; we deliberately draw them all with
    // one style, so there is a single knob here rather than a size hardcoded at every call site.
    // Chrome (panelId == null) is not a window the user can configure, so it keeps the defaults: the
    // status bar's vitals sit in a fixed-height row that a large scale would burst.
    val panelDefaults = LocalPanelDefaults.current
    val effectiveFont = if (panelId != null) font ?: panelDefaults.font else null
    val panelStyle = panelBaseStyle.withFont(effectiveFont)
    val panelScale = if (panelId != null) scale ?: panelDefaults.scale else DEFAULT_PANEL_SCALE
    CompositionLocalProvider(
        LocalContentColor provides style.textColor.toColor(),
        LocalPanelTextStyle provides panelStyle,
    ) {
        PanelObjectLayout(dataObjects = dataObjects, modifier = modifier, scale = panelScale) { data, skinObject ->
            when (data) {
                is PanelObject.Skin -> {
                    PanelSkin(data = data)
                }

                is PanelObject.ProgressBar -> {
                    ProgressBarWithColorMenu(
                        skinObject = skinObject,
                        data = data,
                    )
                }

                is PanelObject.Label -> {
                    Label(skinObject = skinObject, data = data)
                }

                is PanelObject.Link -> {
                    Link(
                        skinObject = skinObject,
                        text = data.value,
                        onClick = { data.cmd?.let { executeWidget(it, data.echo) } },
                    )
                }

                is PanelObject.MenuLink -> {
                    Link(
                        skinObject = skinObject,
                        text = data.value,
                        onClick = { data.exist?.let { onAction(WarlockAction.RequestMenu(it, data.noun)) } },
                    )
                }

                is PanelObject.Image -> {
                    PanelImage(
                        skinObject = skinObject,
                        name = data.name,
                        contentColor = LocalContentColor.current,
                        onClick = data.cmd?.let { cmd -> { executeWidget(cmd, data.echo) } },
                    )
                }

                is PanelObject.MenuImage -> {
                    PanelImage(
                        skinObject = skinObject,
                        name = data.name,
                        contentColor = LocalContentColor.current,
                        onClick =
                            data.exist?.let { exist ->
                                { onAction(WarlockAction.RequestMenu(exist, data.noun)) }
                            },
                    )
                }

                is PanelObject.Button -> {
                    val baseColor = MaterialTheme.colorScheme.primaryContainer
                    val stateLayer = MaterialTheme.colorScheme.onPrimaryContainer
                    val borderBrush = SolidColor(MaterialTheme.colorScheme.outline)
                    PanelButton(
                        onClick = {
                            // A close button sends its command first and dismisses the panel after,
                            // which is the order the real client uses. It goes as one action so the
                            // two halves cannot be reordered on their way to the socket.
                            val closes = panelId?.takeIf { data.closesPanel }
                            if (closes != null) {
                                onAction(
                                    WarlockAction.ClosePanel(
                                        id = closes,
                                        command = data.cmd?.let { substitute(it, values) },
                                        echo = data.echo,
                                    ),
                                )
                            } else {
                                data.cmd?.let { executeWidget(it, data.echo) }
                            }
                        },
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
                            style = LocalPanelTextStyle.current,
                            maxLines = 1,
                        )
                    }
                }

                is PanelObject.DropDownBox -> {
                    DropDownBox(data = data, values = values, executeCommand = execute)
                }

                is PanelObject.Radio -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = data.selected,
                            onClick = { data.cmd?.let(execute) },
                        )
                        data.text?.let {
                            Text(it, style = LocalPanelTextStyle.current, maxLines = 1)
                        }
                    }
                }

                is PanelObject.CheckBox -> {
                    // Seed/refresh the tick and the shared value from the server; local toggles
                    // override them until the next update, the same rule the dropdown and spinner
                    // follow. Either wire value changing is a refresh too, or the value left behind
                    // would be one the box no longer offers.
                    LaunchedEffect(data.id, data.checked, data.checkedValue, data.uncheckedValue) {
                        checkedStates[data.id] = data.checked
                        values[data.id] = if (data.checked) data.checkedValue else data.uncheckedValue
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checkedStates[data.id] ?: data.checked,
                            // Toggling sends nothing: a checkbox only holds a value for another
                            // widget's command to pick up as `%<id>%`. Verified against the real
                            // client, which sends nothing on the click and the new value on the
                            // button that reads it.
                            onCheckedChange = { isChecked ->
                                checkedStates[data.id] = isChecked
                                values[data.id] = if (isChecked) data.checkedValue else data.uncheckedValue
                            },
                        )
                        data.text?.let {
                            Text(it, style = LocalPanelTextStyle.current, maxLines = 1)
                        }
                    }
                }

                is PanelObject.UpDownEditBox -> {
                    UpDownEditBox(data = data, values = values, executeCommand = execute)
                }
            }
        }
    }
}

private val commandVariableRegex = Regex("%([^%]+)%")

// Replaces `%<id>%` placeholders in a command with the current values of the panel's value-bearing
// widgets (e.g. "prep %dDBSpell0%" -> "prep 401"). Unknown placeholders are left as-is.
private fun substitute(
    cmd: String,
    values: Map<String, String>,
): String = commandVariableRegex.replace(cmd) { match -> values[match.groupValues[1]] ?: match.value }

@Composable
private fun DropDownBox(
    data: PanelObject.DropDownBox,
    values: SnapshotStateMap<String, String>,
    executeCommand: (String) -> Unit,
) {
    // Seed/refresh from the server, resolving its label to the option's value so the map holds what
    // `%<id>%` expands to. Local selections below override until the next update.
    LaunchedEffect(data.id, data.value, data.serverOption) { data.applyServerSelection(values) }
    var expanded by remember { mutableStateOf(false) }
    // Empty when the server named an option that does not exist. Its raw value is not a label, and
    // showing it would read as a selection the box does not have.
    val currentLabel = data.optionFor(values[data.id])?.text ?: ""
    Box(modifier = Modifier.padding(2.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(currentLabel, style = LocalPanelTextStyle.current, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            data.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.text, style = LocalPanelTextStyle.current) },
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
    data: PanelObject.UpDownEditBox,
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
            style = LocalPanelTextStyle.current,
        )
        Text(current.toString(), style = LocalPanelTextStyle.current)
        Text(
            "+",
            modifier = Modifier.clickable { step(1) }.padding(horizontal = 6.dp),
            style = LocalPanelTextStyle.current,
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
    data: PanelObject.ProgressBar,
) {
    val settingsState = LocalProgressBarSettings.current
    val setting = settingsState.settings[data.id]
    val barColor = setting?.barColor ?: WarlockColor.Unspecified
    val backgroundColor = setting?.backgroundColor ?: WarlockColor.Unspecified
    val textColor = setting?.textColor ?: WarlockColor.Unspecified
    // Merge this bar's saved font override (if any) over the panel style, so a bar the user has not
    // touched still follows the panel font.
    // Three layers over the panel style, least specific first: what the skin says about this bar,
    // then this bar's saved font override. A bar nobody has skinned or touched still follows the
    // panel font.
    val style =
        LocalPanelTextStyle.current
            .withFont(progressBarSkinFont(skinObject))
            .withFont(
                FontConfig(
                    family = setting?.fontFamily,
                    size = setting?.fontSize,
                    weight = setting?.fontWeight,
                ).takeUnless { it.isEmpty() },
            )

    var menuOpen by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<ProgressBarColorTarget?>(null) }
    var editingFont by remember { mutableStateOf(false) }

    Box {
        PanelProgressBar(
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
    data: PanelObject.Label,
) {
    val colorGroup = skinObject.getColorGroup()
    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            modifier = Modifier.align(data.justify.toAlignment()),
            text = data.value ?: "",
            color = colorGroup.text,
            style = LocalPanelTextStyle.current,
            maxLines = 1,
        )
    }
}

@Composable
private fun Link(
    skinObject: SkinObject?,
    text: String?,
    onClick: () -> Unit,
) {
    // Render a panel link as a low-emphasis text button. The label uses its skin-defined color,
    // falling back to the user-configurable "link" preset (the same one that styles stream-text
    // links), then to the Material primary color.
    val linkPreset = LocalStyleMap.current.getIgnoringCase("link")
    val presetColor = linkPreset?.textColor.toColor().takeOrElse { MaterialTheme.colorScheme.primary }
    val content = skinObject.getColorGroup().text.takeOrElse { presetColor }
    TextButton(
        modifier = Modifier.padding(horizontal = 6.dp),
        onClick = onClick,
    ) {
        Text(
            text = text ?: "",
            color = content,
            style = LocalPanelTextStyle.current,
            maxLines = 1,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun PanelSkin(data: PanelObject.Skin) {
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
