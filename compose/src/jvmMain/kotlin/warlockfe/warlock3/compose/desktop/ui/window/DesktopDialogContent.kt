package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.broken_image
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.window.DialogObjectLayout
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.util.getIgnoringCase
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.io.encoding.Base64

private val labelSmallStyle =
    TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    )

private val labelTinyStyle = labelSmallStyle.copy(fontSize = 9.sp)

@Composable
fun DesktopDialogContent(
    dataObjects: List<DialogObject>,
    executeCommand: (String) -> Unit,
    style: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    DialogObjectLayout(dataObjects = dataObjects, modifier = modifier) { data, skinObject ->
        when (data) {
            is DialogObject.Skin -> DialogSkin(data = data)
            is DialogObject.ProgressBar ->
                ProgressBar(
                    skinObject = skinObject,
                    data = data,
                    defaultStyle = style,
                )
            is DialogObject.Label -> Label(skinObject = skinObject, data = data, defaultStyle = style)
            is DialogObject.Link ->
                Link(
                    skinObject = skinObject,
                    data = data,
                    executeCommand = executeCommand,
                    defaultStyle = style,
                )
            is DialogObject.Image ->
                DialogImage(
                    skinObject = skinObject,
                    data = data,
                    executeCommand = executeCommand,
                    defaultStyle = style,
                )
            is DialogObject.Button ->
                DialogButton(
                    data = data,
                    executeCommand = executeCommand,
                )
        }
    }
}

@Composable
private fun ProgressBar(
    skinObject: SkinObject?,
    data: DialogObject.ProgressBar,
    defaultStyle: StyleDefinition,
) {
    val colorGroup = skinObject.getColorGroup(defaultStyle)
    val percent = data.value.value
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier) {
        drawRect(
            color = colorGroup.background,
            topLeft = Offset(1f, 1f),
            size = Size(height = size.height - 2f, width = size.width - 2f),
        )
        drawRect(
            color = colorGroup.bar,
            topLeft = Offset(1f, 1f),
            size = Size(width = (size.width - 2f) * percent / 100, height = size.height - 2f),
        )
        data.text?.let { text ->
            val measuredText =
                textMeasurer.measure(
                    text = text,
                    constraints = Constraints(maxWidth = size.width.toInt()),
                    maxLines = 1,
                    style = labelSmallStyle,
                )
            drawText(
                textLayoutResult = measuredText,
                color = colorGroup.text,
                topLeft = Offset(x = (size.width - measuredText.size.width) / 2f, y = (size.height - measuredText.size.height) / 2f),
            )
        }
    }
}

@Composable
private fun Label(
    skinObject: SkinObject?,
    data: DialogObject.Label,
    defaultStyle: StyleDefinition,
) {
    val colorGroup = skinObject.getColorGroup(defaultStyle)
    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = data.value ?: "",
            color = colorGroup.text,
            style = labelTinyStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun Link(
    skinObject: SkinObject?,
    data: DialogObject.Link,
    executeCommand: (String) -> Unit,
    defaultStyle: StyleDefinition,
) {
    val colorGroup = skinObject.getColorGroup(defaultStyle)
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
            style = labelTinyStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun DialogImage(
    skinObject: SkinObject?,
    data: DialogObject.Image,
    executeCommand: (String) -> Unit,
    defaultStyle: StyleDefinition,
) {
    val skin = LocalSkin.current
    val colorGroup = skinObject.getColorGroup(defaultStyle)
    val imageData = data.name?.let { skin.getIgnoringCase(it) }
    Box(
        modifier =
            if (data.cmd != null) {
                Modifier.clickable {
                    executeCommand(data.cmd!!)
                }
            } else {
                Modifier
            },
    ) {
        val image = (imageData?.image ?: skinObject?.image)?.data?.let { Base64.decode(it) }
        if (image != null) {
            AsyncImage(image, contentDescription = null)
        } else {
            androidx.compose.foundation.Image(
                painter = painterResource(Res.drawable.broken_image),
                colorFilter = ColorFilter.tint(colorGroup.bar),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun DialogButton(
    data: DialogObject.Button,
    executeCommand: (String) -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    val borderColor = JewelTheme.globalColors.borders.normal
    val backgroundColor = JewelTheme.globalColors.panelBackground
    val textColor = JewelTheme.globalColors.text.normal
    Box(
        modifier =
            Modifier
                .background(backgroundColor, shape)
                .border(width = Dp.Hairline, color = borderColor, shape = shape),
    ) {
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
            color = textColor,
            style = labelTinyStyle,
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

private data class ColorGroup(
    val text: Color,
    val bar: Color,
    val background: Color,
)

private fun SkinObject?.getColorGroup(defaultStyle: StyleDefinition): ColorGroup =
    ColorGroup(
        text = (this?.color?.toWarlockColor() ?: defaultStyle.textColor).toColor(),
        bar = this?.bar?.toWarlockColor()?.toColor() ?: Color.Blue,
        background = (this?.background?.toWarlockColor() ?: defaultStyle.backgroundColor).toColor(),
    )
