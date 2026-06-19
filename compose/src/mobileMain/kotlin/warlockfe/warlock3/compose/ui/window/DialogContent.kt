package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import warlockfe.warlock3.compose.model.SkinColor
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.client.Percentage
import warlockfe.warlock3.core.text.StyleDefinition
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
                    ProgressBar(
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

@Composable
private fun ProgressBar(
    skinObject: SkinObject?,
    data: DialogObject.ProgressBar,
) {
    val colorGroup = skinObject.getColorGroup()
    val percent = data.value.value
    val textMeasurer = rememberTextMeasurer()
    val font = MaterialTheme.typography.labelSmall
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
                    style = font,
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
