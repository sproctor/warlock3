package warlockfe.warlock3.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.decodeToImageBitmap
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.compass.Direction
import kotlin.io.encoding.Base64
import kotlin.math.roundToInt

private const val DEFAULT_COMPASS_WIDTH = 81
private const val DEFAULT_COMPASS_HEIGHT = 49

@Composable
fun CompassView(
    height: Dp,
    directions: Set<Direction>,
    onClick: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val children = skin["compass"]?.children ?: emptyMap()

    // Decode each sprite sheet (background + the "lit" sheets) once per skin.
    val sprites =
        remember(skin) {
            children
                .mapNotNull { (key, child) ->
                    child.image?.data?.let { data ->
                        runCatching { Base64.decode(data).decodeToImageBitmap() }.getOrNull()?.let { key to it }
                    }
                }.toMap()
        }
    val background = sprites["background"]
    val compassWidth = background?.width ?: DEFAULT_COMPASS_WIDTH
    val compassHeight = background?.height ?: DEFAULT_COMPASS_HEIGHT

    val scale = with(LocalDensity.current) { height.toPx() } / compassHeight
    val width = height * (compassWidth.toFloat() / compassHeight.toFloat())

    Canvas(
        modifier =
            modifier
                .size(width = width, height = height)
                .pointerInput(children, directions, scale) {
                    detectTapGestures { offset ->
                        val x = offset.x / scale
                        val y = offset.y / scale
                        directions
                            .mapNotNull { direction -> children[direction.value]?.let { direction to it.compassRect() } }
                            .filter { (_, rect) -> x >= rect.left && x < rect.right && y >= rect.top && y < rect.bottom }
                            // Smallest hit-rect wins so the small cardinal/diagonal arrows take
                            // priority over the large up/down halves they overlap.
                            .minByOrNull { (_, rect) -> rect.width * rect.height }
                            ?.let { (direction, _) -> onClick(direction) }
                    }
                },
    ) {
        background?.let {
            drawImage(
                image = it,
                dstSize = IntSize((compassWidth * scale).roundToInt(), (compassHeight * scale).roundToInt()),
            )
        }
        directions.forEach { direction ->
            val child = children[direction.value] ?: return@forEach
            val image = child.sprite?.let { sprites[it] } ?: return@forEach
            val rect = child.compassRect()
            drawImage(
                image = image,
                srcOffset = IntOffset(rect.left, rect.top),
                srcSize = IntSize(rect.width, rect.height),
                dstOffset = IntOffset((rect.left * scale).roundToInt(), (rect.top * scale).roundToInt()),
                dstSize = IntSize((rect.width * scale).roundToInt(), (rect.height * scale).roundToInt()),
            )
        }
    }
}

private fun SkinObject.compassRect(): IntRect =
    IntRect(
        offset = IntOffset(left ?: 0, top ?: 0),
        size = IntSize(width ?: 0, height ?: 0),
    )

@Preview
@Composable
private fun EmptyCompassPreview() {
    CompositionLocalProvider(LocalSkin provides previewCompassSkin()) {
        CompassView(
            height = 88.dp,
            directions = emptySet(),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun CompassPreview() {
    CompositionLocalProvider(LocalSkin provides previewCompassSkin()) {
        CompassView(
            height = 88.dp,
            directions =
                setOf(
                    Direction("n"),
                    Direction("e"),
                    Direction("W"),
                    Direction("out"),
                    Direction("up"),
                ),
            onClick = {},
        )
    }
}
