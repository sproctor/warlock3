package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.linkStyle
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerDialog
import warlockfe.warlock3.compose.desktop.components.DesktopFontPickerDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockDropdownSelect
import warlockfe.warlock3.compose.desktop.shim.WarlockRadioButtonRow
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.window.LocalProgressBarSettings
import warlockfe.warlock3.compose.ui.window.PanelButton
import warlockfe.warlock3.compose.ui.window.PanelImage
import warlockfe.warlock3.compose.ui.window.PanelObjectLayout
import warlockfe.warlock3.compose.ui.window.PanelProgressBar
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.LocalStyleMap
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.toAlignment
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.getIgnoringCase
import kotlin.io.encoding.Base64

private val labelStyle
    @Composable
    get() =
        JewelTheme.defaultTextStyle.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )

@Composable
fun DesktopPanelContent(
    dataObjects: List<PanelObject>,
    onAction: (WarlockAction) -> Unit,
    style: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    // Value-bearing widgets (dropdowns, spinners) publish their current value here keyed by id; other
    // widgets' commands reference them as %<id>% (e.g. "prep %dDBSpell0%", "quickstrike %uDEQuickstrike%").
    // A spinner has no command of its own, so its value lives here until another widget consumes it.
    val values = remember { mutableStateMapOf<String, String>() }
    val executeWidget: (String, String?) -> Unit = { cmd, echo ->
        onAction(WarlockAction.SendWidgetCommand(substitute(cmd, values), echo))
    }
    val execute: (String) -> Unit = { executeWidget(it, null) }
    CompositionLocalProvider(
        LocalContentColor provides style.textColor.toColor(),
    ) {
        PanelObjectLayout(dataObjects = dataObjects, modifier = modifier) { data, skinObject ->
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
                    val colors = JewelTheme.defaultButtonStyle.colors
                    PanelButton(
                        onClick = { data.cmd?.let { executeWidget(it, data.echo) } },
                        modifier = Modifier.padding(2.dp),
                        shape = RoundedCornerShape(2.dp),
                        background = { isHovered, isPressed ->
                            when {
                                isPressed -> colors.backgroundPressed
                                isHovered -> colors.backgroundHovered
                                else -> colors.background
                            }
                        },
                        border = { isHovered, isPressed ->
                            when {
                                isPressed -> colors.borderPressed
                                isHovered -> colors.borderHovered
                                else -> colors.border
                            }
                        },
                    ) { isHovered, isPressed ->
                        val textColor =
                            when {
                                isPressed -> colors.contentPressed
                                isHovered -> colors.contentHovered
                                else -> colors.content
                            }
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = data.value ?: "",
                            color = textColor,
                            style = labelStyle,
                            maxLines = 1,
                        )
                    }
                }

                is PanelObject.DropDownBox -> {
                    // Seed/refresh from the server, resolving its label to the option's value so the
                    // map holds what `%<id>%` expands to. Local selections override until the next
                    // update.
                    LaunchedEffect(data.id, data.value, data.serverOption) { data.applyServerSelection(values) }
                    if (data.options.isEmpty()) {
                        Box {}
                    } else {
                        // Null when the server named an option that does not exist, which the box has
                        // to show as nothing selected rather than as some other option.
                        WarlockDropdownSelect(
                            items = data.options,
                            selected = data.optionFor(values[data.id]),
                            onSelect = { option ->
                                values[data.id] = option.value
                                data.cmd?.let(execute)
                            },
                            modifier = Modifier.padding(2.dp),
                            itemLabelBuilder = { it.text },
                            textStyle = labelStyle,
                        )
                    }
                }

                is PanelObject.Radio -> {
                    WarlockRadioButtonRow(
                        selected = data.selected,
                        onClick = { data.cmd?.let(execute) },
                        text = data.text ?: "",
                    )
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
            style = labelStyle,
        )
        Text(current.toString(), style = labelStyle)
        Text(
            "+",
            modifier = Modifier.clickable { step(1) }.padding(horizontal = 6.dp),
            style = labelStyle,
        )
    }
}

private enum class ProgressBarColorTarget {
    Bar,
    Background,
    Text,
}

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
    // Merge the saved font override (if any) onto the base label style.
    val style =
        labelStyle.copy(
            fontFamily = setting?.fontFamily?.let { createFontFamily(it) } ?: labelStyle.fontFamily,
            fontSize = setting?.fontSize?.sp ?: labelStyle.fontSize,
            fontWeight = setting?.fontWeight?.let { FontWeight(it) } ?: labelStyle.fontWeight,
        )

    var editingTarget by remember { mutableStateOf<ProgressBarColorTarget?>(null) }
    var editingFont by remember { mutableStateOf(false) }

    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("Change bar color ...") { editingTarget = ProgressBarColorTarget.Bar },
                ContextMenuItem("Change background color ...") { editingTarget = ProgressBarColorTarget.Background },
                ContextMenuItem("Change text color ...") { editingTarget = ProgressBarColorTarget.Text },
                ContextMenuItem("Change font ...") { editingFont = true },
                ContextMenuItem("Reset colors") {
                    settingsState.saveColors(
                        data.id,
                        WarlockColor.Unspecified,
                        WarlockColor.Unspecified,
                        WarlockColor.Unspecified,
                    )
                },
            )
        },
    ) {
        PanelProgressBar(
            modifier = Modifier.fillMaxSize(),
            skinObject = skinObject,
            data = data,
            barColorOverride = barColor,
            backgroundColorOverride = backgroundColor,
            textColorOverride = textColor,
            style = style,
        )
    }

    editingTarget?.let { target ->
        val current =
            when (target) {
                ProgressBarColorTarget.Bar -> barColor
                ProgressBarColorTarget.Background -> backgroundColor
                ProgressBarColorTarget.Text -> textColor
            }
        DesktopColorPickerDialog(
            initialColor = current.toColor(),
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
        DesktopFontPickerDialog(
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
            style = labelStyle,
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
    // A panel link uses its skin-defined text color, falling back to the user-configurable "link"
    // preset (the same one that styles links in the text stream), then to Jewel's themed link color.
    // It keeps that color across the interactive states (the always-on underline is the affordance),
    // dims a little while pressed, and fades further when disabled.
    val linkPreset = LocalStyleMap.current.getIgnoringCase("link")
    val presetColor = linkPreset?.textColor.toColor().takeOrElse { JewelTheme.linkStyle.colors.content }
    val linkColor = skinObject.getColorGroup().text.takeOrElse { presetColor }
    val colors =
        LinkColors(
            content = linkColor,
            contentHovered = linkColor,
            contentFocused = linkColor,
            contentPressed = linkColor.copy(alpha = 0.7f),
            contentVisited = linkColor,
            contentDisabled = linkColor.copy(alpha = 0.4f),
        )
    Link(
        modifier = Modifier.padding(horizontal = 6.dp),
        text = text ?: "",
        onClick = onClick,
        textStyle = labelStyle,
        style =
            LinkStyle(
                colors = colors,
                metrics = JewelTheme.linkStyle.metrics,
                icons = JewelTheme.linkStyle.icons,
                underlineBehavior = LinkUnderlineBehavior.ShowAlways,
            ),
    )
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
