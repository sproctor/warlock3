package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerDialog
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.window.DialogButton
import warlockfe.warlock3.compose.ui.window.DialogImage
import warlockfe.warlock3.compose.ui.window.DialogObjectLayout
import warlockfe.warlock3.compose.ui.window.DialogProgressBar
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.getIgnoringCase
import kotlin.io.encoding.Base64

private val labelStyle =
    TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )

@Composable
fun DesktopDialogContent(
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
                    val colors = JewelTheme.defaultButtonStyle.colors
                    DialogButton(
                        onClick = { executeCommand(data.cmd ?: "") },
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
            }
        }
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
    data: DialogObject.ProgressBar,
) {
    val colorState = LocalProgressBarColors.current
    val setting = colorState.settings[data.id]
    val barColor = setting?.barColor ?: WarlockColor.Unspecified
    val backgroundColor = setting?.backgroundColor ?: WarlockColor.Unspecified
    val textColor = setting?.textColor ?: WarlockColor.Unspecified

    var editingTarget by remember { mutableStateOf<ProgressBarColorTarget?>(null) }

    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem("Change bar color ...") { editingTarget = ProgressBarColorTarget.Bar },
                ContextMenuItem("Change background color ...") { editingTarget = ProgressBarColorTarget.Background },
                ContextMenuItem("Change text color ...") { editingTarget = ProgressBarColorTarget.Text },
                ContextMenuItem("Reset colors") {
                    colorState.saveColors(
                        data.id,
                        WarlockColor.Unspecified,
                        WarlockColor.Unspecified,
                        WarlockColor.Unspecified,
                    )
                },
            )
        },
    ) {
        DialogProgressBar(
            modifier = Modifier.fillMaxSize(),
            skinObject = skinObject,
            data = data,
            barColorOverride = barColor,
            backgroundColorOverride = backgroundColor,
            textColorOverride = textColor,
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
            style = labelStyle,
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
            style = labelStyle,
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
