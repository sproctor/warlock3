package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.util.getIgnoringCase

@Composable
fun DialogObjectLayout(
    dataObjects: List<DialogObject>,
    modifier: Modifier = Modifier,
    content: @Composable (data: DialogObject, skinObject: SkinObject?) -> Unit,
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

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            dataObjects.forEach { data ->
                content(data, skinObjects.getIgnoringCase(data.id))
            }
        },
    ) { measurables, constraints ->
        val widthBasis =
            if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val heightBasis =
            if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val progressBarHeightPx = 16.dp.roundToPx()

        val placements = mutableMapOf<String, ItemPlacement>()
        var lastPlacement: ItemPlacement? = null

        dataObjects.forEachIndexed { index, data ->
            val skinObject = skinObjects.getIgnoringCase(data.id)
            val imageData = (data as? DialogObject.Image)?.name?.let { skin.getIgnoringCase(it) }
            val parentSkinPlacement = parentSkins.getIgnoringCase(data.id)?.let { placements[it] }

            val widthSource =
                (imageData?.width ?: skinObject?.width)?.let { DataDistance.Pixels(it) } ?: data.width
            val heightSource =
                (imageData?.height ?: skinObject?.height)?.let { DataDistance.Pixels(it) } ?: data.height

            val targetWidth = widthSource?.toPx(widthBasis, this)
            val targetHeight =
                heightSource?.toPx(heightBasis, this)
                    ?: progressBarHeightPx.takeIf { data is DialogObject.ProgressBar }

            val childConstraints =
                Constraints(
                    minWidth = targetWidth ?: 0,
                    maxWidth = targetWidth ?: constraints.maxWidth,
                    minHeight = targetHeight ?: 0,
                    maxHeight = targetHeight ?: constraints.maxHeight,
                )

            val placeable = measurables[index].measure(childConstraints)

            val dataTop = skinObject?.top?.let { DataDistance.Pixels(it) } ?: data.top
            val dataLeft = skinObject?.left?.let { DataDistance.Pixels(it) } ?: data.left

            val topMargin = dataTop?.toPx(widthBasis, this) ?: 0
            val leftMargin = dataLeft?.toPx(widthBasis, this) ?: 0

            val y =
                when {
                    data.topAnchor != null ->
                        (placements[data.topAnchor]?.bottom ?: 0) + topMargin
                    dataTop != null ->
                        (parentSkinPlacement?.y ?: 0) + topMargin
                    data.leftAnchor != null ->
                        placements[data.leftAnchor]?.y ?: 0
                    dataLeft != null ->
                        lastPlacement?.bottom ?: 0
                    else ->
                        lastPlacement?.y ?: 0
                }

            val x =
                if (data.leftAnchor != null) {
                    (placements[data.leftAnchor]?.right ?: 0) + leftMargin
                } else {
                    when (data.align) {
                        "n" -> (widthBasis - placeable.width) / 2 + leftMargin
                        "ne" -> widthBasis - placeable.width - leftMargin
                        else ->
                            if (dataLeft == null) {
                                (lastPlacement?.right ?: 0) + leftMargin
                            } else {
                                (parentSkinPlacement?.x ?: 0) + leftMargin
                            }
                    }
                }

            val placement = ItemPlacement(x = x, y = y, placeable = placeable)
            placements[data.id] = placement
            lastPlacement = placement
        }

        val contentWidth = placements.values.maxOfOrNull { it.right } ?: 0
        val contentHeight = placements.values.maxOfOrNull { it.bottom } ?: 0
        val layoutWidth =
            if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                contentWidth.coerceAtLeast(constraints.minWidth)
            }
        val layoutHeight =
            if (constraints.hasBoundedHeight) {
                constraints.maxHeight
            } else {
                contentHeight.coerceAtLeast(constraints.minHeight)
            }

        layout(layoutWidth, layoutHeight) {
            placements.values.forEach { it.placeable.place(it.x, it.y) }
        }
    }
}

private data class ItemPlacement(
    val x: Int,
    val y: Int,
    val placeable: Placeable,
) {
    val right: Int get() = x + placeable.width
    val bottom: Int get() = y + placeable.height
}

private fun DataDistance.toPx(
    basis: Int,
    density: Density,
): Int =
    when (this) {
        is DataDistance.Percent -> basis * value.value / 100
        is DataDistance.Pixels -> with(density) { value.dp.roundToPx() }
    }
