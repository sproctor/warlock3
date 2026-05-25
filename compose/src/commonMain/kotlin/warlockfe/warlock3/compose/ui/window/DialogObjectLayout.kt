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

        val itemInfos =
            dataObjects.mapIndexed { index, data ->
                val skinObject = skinObjects.getIgnoringCase(data.id)
                val imageData = (data as? DialogObject.Image)?.name?.let { skin.getIgnoringCase(it) }

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

                ItemInfo(
                    data = data,
                    placeable = placeable,
                    dataTop = dataTop,
                    dataLeft = dataLeft,
                    topMargin = dataTop?.toPx(widthBasis, this) ?: 0,
                    leftMargin = dataLeft?.toPx(widthBasis, this) ?: 0,
                    parentSkinId = parentSkins.getIgnoringCase(data.id),
                )
            }

        // Resolve placements in topological order so an item's anchors/parent are
        // already placed when we read them. Items may reference anchors that appear
        // later in the data list because the dialog state appends updated items to
        // the end. Drawing still happens in data order to preserve z-ordering.
        val indexById = itemInfos.withIndex().associate { (idx, info) -> info.data.id to idx }
        val visitState = IntArray(itemInfos.size) // 0=unvisited, 1=in-progress, 2=done
        val placementOrder = ArrayList<Int>(itemInfos.size)

        fun visit(index: Int) {
            // Skip if already placed; also skip if currently in-progress to break cycles.
            if (visitState[index] != 0) return
            visitState[index] = 1
            val info = itemInfos[index]
            val data = info.data
            data.topAnchor?.let { indexById[it] }?.let(::visit)
            data.leftAnchor?.let { indexById[it] }?.let(::visit)
            info.parentSkinId?.let { indexById[it] }?.let(::visit)
            if (info.usesLastPlacement() && index > 0) visit(index - 1)
            visitState[index] = 2
            placementOrder += index
        }

        for (i in itemInfos.indices) visit(i)

        val placements = HashMap<String, ItemPlacement>(itemInfos.size)

        for (index in placementOrder) {
            val info = itemInfos[index]
            val data = info.data
            val parentSkinPlacement = info.parentSkinId?.let { placements[it] }
            val lastPlacement = if (index > 0) placements[itemInfos[index - 1].data.id] else null

            val y =
                when {
                    data.topAnchor != null ->
                        (placements[data.topAnchor]?.bottom ?: 0) + info.topMargin
                    info.dataTop != null ->
                        (parentSkinPlacement?.y ?: 0) + info.topMargin
                    data.leftAnchor != null ->
                        placements[data.leftAnchor]?.y ?: 0
                    info.dataLeft != null ->
                        lastPlacement?.bottom ?: 0
                    else ->
                        lastPlacement?.y ?: 0
                }

            val x =
                if (data.leftAnchor != null) {
                    (placements[data.leftAnchor]?.right ?: 0) + info.leftMargin
                } else {
                    when (data.align) {
                        "n" -> (widthBasis - info.placeable.width) / 2 + info.leftMargin
                        "ne" -> widthBasis - info.placeable.width - info.leftMargin
                        else ->
                            if (info.dataLeft == null) {
                                (lastPlacement?.right ?: 0) + info.leftMargin
                            } else {
                                (parentSkinPlacement?.x ?: 0) + info.leftMargin
                            }
                    }
                }

            placements[data.id] = ItemPlacement(x = x, y = y, placeable = info.placeable)
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
            for (info in itemInfos) {
                placements[info.data.id]?.let { it.placeable.place(it.x, it.y) }
            }
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

private class ItemInfo(
    val data: DialogObject,
    val placeable: Placeable,
    val dataTop: DataDistance?,
    val dataLeft: DataDistance?,
    val topMargin: Int,
    val leftMargin: Int,
    val parentSkinId: String?,
) {
    // True when the placement formula will read lastPlacement (the data-order
    // predecessor's placement). Mirrors the branches in y/x computation.
    fun usesLastPlacement(): Boolean {
        val needsForY = data.topAnchor == null && dataTop == null && data.leftAnchor == null
        val needsForX = data.leftAnchor == null && data.align != "n" && data.align != "ne" && dataLeft == null
        return needsForY || needsForX
    }
}

private fun DataDistance.toPx(
    basis: Int,
    density: Density,
): Int =
    when (this) {
        is DataDistance.Percent -> basis * value.value / 100
        is DataDistance.Pixels -> with(density) { value.dp.roundToPx() }
    }
