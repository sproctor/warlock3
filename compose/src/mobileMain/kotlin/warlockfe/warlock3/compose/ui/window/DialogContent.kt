package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
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
                    DialogProgressBar(
                        skinObject = skinObject,
                        data = data,
                        style = MaterialTheme.typography.labelSmall,
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
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
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
    // Render a dialog link as a low-emphasis text button: no fill or border at rest, a primary-tinted
    // state layer on hover/press, and a primary-colored label (falling back to any skin-defined color).
    val accent = MaterialTheme.colorScheme.primary
    val content = skinObject.getColorGroup().text.takeOrElse { accent }
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
