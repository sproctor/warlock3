package warlockfe.warlock3.compose.desktop.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.constraintlayout.compose.ConstrainedLayoutReference
import androidx.constraintlayout.compose.ConstraintLayout
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.broken_image
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DataDistance
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
    val skin = LocalSkin.current
    val skinObjects = mutableMapOf<String, SkinObject>()
    val parentSkins = mutableMapOf<String, String>()
    dataObjects.forEach { data ->
        if (data is DialogObject.Skin) {
            val skinObject = skin.getIgnoringCase(data.name)
            if (skinObject != null) {
                data.controls.forEach { id ->
                    skinObject.children.getIgnoringCase(id)?.let {
                        skinObjects[id] = it
                    }
                    parentSkins[id] = data.id
                }
            }
        }
    }

    BoxWithConstraints(modifier) {
        ConstraintLayout(
            Modifier.fillMaxSize(),
        ) {
            val refs = dataObjects.associate { it.id to createRef() }
            var lastRef: ConstrainedLayoutReference? = null
            dataObjects.forEach { data ->
                val skinObject = skinObjects.getIgnoringCase(data.id)
                val parentSkin = parentSkins.getIgnoringCase(data.id)?.let { refs[it] }
                this@BoxWithConstraints.DataObjectContent(
                    modifier =
                        Modifier
                            .constrainAs(refs[data.id]!!) {
                                val dataTop = skinObject?.top?.let { DataDistance.Pixels(it) } ?: data.top
                                val dataLeft = skinObject?.left?.let { DataDistance.Pixels(it) } ?: data.left
                                val topAnchor =
                                    if (data.topAnchor != null) {
                                        refs[data.topAnchor]?.bottom
                                    } else if (dataTop != null) {
                                        parentSkin?.top
                                    } else if (data.leftAnchor != null) {
                                        refs[data.leftAnchor]?.top
                                    } else if (dataLeft != null) {
                                        lastRef?.bottom
                                    } else {
                                        lastRef?.top
                                    }
                                top.linkTo(
                                    anchor = topAnchor ?: parent.top,
                                    margin = dataTop?.toDp(this@BoxWithConstraints.maxWidth) ?: 0.dp,
                                )
                                val leftMargin = dataLeft?.toDp(this@BoxWithConstraints.maxWidth) ?: 0.dp
                                if (data.leftAnchor != null) {
                                    absoluteLeft.linkTo(
                                        anchor = refs[data.leftAnchor]?.absoluteRight ?: parent.absoluteLeft,
                                        margin = leftMargin,
                                    )
                                } else {
                                    when (data.align) {
                                        "n" -> {
                                            absoluteLeft.linkTo(parent.absoluteLeft, leftMargin)
                                            absoluteRight.linkTo(parent.absoluteRight, -leftMargin)
                                        }

                                        "ne" -> {
                                            absoluteRight.linkTo(parent.absoluteRight, leftMargin)
                                        }

                                        else -> {
                                            val leftAnchor =
                                                if (dataLeft == null) {
                                                    lastRef?.absoluteRight ?: parent.absoluteLeft
                                                } else {
                                                    parentSkin?.absoluteLeft ?: parent.absoluteLeft
                                                }
                                            absoluteLeft.linkTo(leftAnchor, leftMargin)
                                        }
                                    }
                                }
                                lastRef = refs[data.id]
                            },
                    skinObject = skinObjects.getIgnoringCase(data.id),
                    dataObject = data,
                    executeCommand = executeCommand,
                    defaultStyle = style,
                )
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.DataObjectContent(
    skinObject: SkinObject?,
    dataObject: DialogObject,
    executeCommand: (String) -> Unit,
    defaultStyle: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    when (dataObject) {
        is DialogObject.Skin -> DialogSkin(data = dataObject, modifier = modifier)
        is DialogObject.ProgressBar ->
            ProgressBar(
                skinObject = skinObject,
                data = dataObject,
                defaultStyle = defaultStyle,
                modifier = modifier,
            )
        is DialogObject.Label -> Label(skinObject = skinObject, data = dataObject, defaultStyle = defaultStyle, modifier = modifier)
        is DialogObject.Link ->
            Link(
                skinObject = skinObject,
                data = dataObject,
                executeCommand = executeCommand,
                defaultStyle = defaultStyle,
                modifier = modifier,
            )
        is DialogObject.Image ->
            DialogImage(
                skinObject = skinObject,
                data = dataObject,
                executeCommand = executeCommand,
                defaultStyle = defaultStyle,
                modifier = modifier,
            )
        is DialogObject.Button ->
            DialogButton(
                skinObject = skinObject,
                data = dataObject,
                executeCommand = executeCommand,
                modifier = modifier,
            )
    }
}

private fun DataDistance.toDp(maxWidth: Dp): Dp =
    when (this) {
        is DataDistance.Percent -> maxWidth * value.value / 100
        is DataDistance.Pixels -> value.dp
    }

@Composable
private fun BoxWithConstraintsScope.ProgressBar(
    skinObject: SkinObject?,
    data: DialogObject.ProgressBar,
    defaultStyle: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    val colorGroup = skinObject.getColorGroup(defaultStyle)
    val percent = data.value.value
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier =
            modifier
                .size(
                    width =
                        (skinObject?.width?.let { DataDistance.Pixels(it) } ?: data.width)?.toDp(maxWidth)
                            ?: Dp.Unspecified,
                    height = (
                        (skinObject?.height?.let { DataDistance.Pixels(it) } ?: data.height)?.toDp(maxHeight)
                            ?: 16.dp
                    ),
                ),
    ) {
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
private fun BoxWithConstraintsScope.Label(
    skinObject: SkinObject?,
    data: DialogObject.Label,
    defaultStyle: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    val colorGroup = skinObject.getColorGroup(defaultStyle)
    Box(
        modifier =
            modifier
                .size(
                    width =
                        (skinObject?.width?.let { DataDistance.Pixels(it) } ?: data.width)?.toDp(maxWidth)
                            ?: Dp.Unspecified,
                    height =
                        (skinObject?.height?.let { DataDistance.Pixels(it) } ?: data.height)?.toDp(maxHeight)
                            ?: Dp.Unspecified,
                ).padding(horizontal = 4.dp),
    ) {
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
private fun BoxWithConstraintsScope.Link(
    skinObject: SkinObject?,
    data: DialogObject.Link,
    executeCommand: (String) -> Unit,
    defaultStyle: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    val colorGroup = skinObject.getColorGroup(defaultStyle)
    Box(
        modifier =
            modifier
                .size(
                    width =
                        (skinObject?.width?.let { DataDistance.Pixels(it) } ?: data.width)?.toDp(maxWidth)
                            ?: Dp.Unspecified,
                    height =
                        (skinObject?.height?.let { DataDistance.Pixels(it) } ?: data.height)?.toDp(maxHeight)
                            ?: Dp.Unspecified,
                ).padding(horizontal = 4.dp),
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
            color = colorGroup.text,
            style = labelTinyStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.DialogImage(
    skinObject: SkinObject?,
    data: DialogObject.Image,
    executeCommand: (String) -> Unit,
    defaultStyle: StyleDefinition,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colorGroup = skinObject.getColorGroup(defaultStyle)
    val imageData = data.name?.let { skin.getIgnoringCase(it) }
    Box(
        modifier =
            modifier
                .size(
                    width =
                        ((imageData?.width ?: skinObject?.width)?.let { DataDistance.Pixels(it) } ?: data.width)?.toDp(
                            maxWidth,
                        )
                            ?: Dp.Unspecified,
                    height =
                        (
                            (imageData?.height ?: skinObject?.height)?.let { DataDistance.Pixels(it) }
                                ?: data.height
                        )?.toDp(maxHeight)
                            ?: Dp.Unspecified,
                ).then(
                    if (data.cmd != null) {
                        Modifier.clickable {
                            executeCommand(data.cmd!!)
                        }
                    } else {
                        Modifier
                    },
                ),
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
private fun BoxWithConstraintsScope.DialogButton(
    skinObject: SkinObject?,
    data: DialogObject.Button,
    executeCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(2.dp)
    val borderColor = JewelTheme.globalColors.borders.normal
    val backgroundColor = JewelTheme.globalColors.panelBackground
    val textColor = JewelTheme.globalColors.text.normal
    Box(
        modifier =
            modifier
                .size(
                    width =
                        (skinObject?.width?.let { DataDistance.Pixels(it) } ?: data.width)?.toDp(maxWidth)
                            ?: Dp.Unspecified,
                    height =
                        (skinObject?.height?.let { DataDistance.Pixels(it) } ?: data.height)?.toDp(maxHeight)
                            ?: Dp.Unspecified,
                ).background(backgroundColor, shape)
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
private fun BoxWithConstraintsScope.DialogSkin(
    data: DialogObject.Skin,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val skinObject = skin.getIgnoringCase(data.name)
    val image = skinObject?.image?.data?.let { Base64.decode(it) }
    Box(
        modifier
            .size(
                width =
                    (skinObject?.width?.let { DataDistance.Pixels(it) } ?: data.width)?.toDp(maxWidth)
                        ?: Dp.Unspecified,
                height =
                    (skinObject?.height?.let { DataDistance.Pixels(it) } ?: data.height)?.toDp(maxHeight)
                        ?: Dp.Unspecified,
            ),
    ) {
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
