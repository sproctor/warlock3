package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DialogObject
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
                        executeCommand = executeCommand,
                    )
                }

                is DialogObject.Image -> {
                    DialogImage(
                        skinObject = skinObject,
                        data = data,
                        executeCommand = executeCommand,
                        contentColor = LocalContentColor.current,
                    )
                }

                is DialogObject.Button -> {
                    val baseColor = MaterialTheme.colorScheme.primaryContainer
                    val stateLayer = MaterialTheme.colorScheme.onPrimaryContainer
                    val borderBrush = SolidColor(MaterialTheme.colorScheme.outline)
                    DialogButton(
                        onClick = { executeCommand(data.cmd ?: "") },
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
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
 * desktop right-click color menu. Reads and persists through [LocalProgressBarColors].
 */
@Composable
private fun ProgressBarWithColorMenu(
    skinObject: SkinObject?,
    data: DialogObject.ProgressBar,
) {
    val colorState = LocalProgressBarColors.current
    val setting = colorState.settings[data.id]
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
                    colorState.saveColors(
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
                colorState.saveColors(
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
            currentStyle =
                StyleDefinition(
                    fontFamily = setting?.fontFamily,
                    fontSize = setting?.fontSize,
                    fontWeight = setting?.fontWeight,
                ),
            onCloseRequest = { editingFont = false },
            onSaveClick = { fontUpdate ->
                colorState.saveFont(data.id, fontUpdate.fontFamily, fontUpdate.size, fontUpdate.weight)
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
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
    val colorGroup = skinObject.getColorGroup()
    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text =
                buildAnnotatedString {
                    pushLink(
                        LinkAnnotation.Clickable("action") {
                            executeCommand(data.cmd ?: "")
                        },
                    )
                    append(data.value)
                    pop()
                },
            color = colorGroup.text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
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
