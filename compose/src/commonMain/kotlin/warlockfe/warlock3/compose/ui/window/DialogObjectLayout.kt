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

/**
 * Lays out the objects of a game "dialog" panel (progress bars, labels, links, images, ...).
 *
 * A dialog rarely gives absolute coordinates for everything; each object is positioned by one of a
 * handful of rules. Vertically (highest priority first): below a sibling it `topAnchor`s to, at an
 * explicit `top` offset inside its parent skin container, on the row of a `leftAnchor` sibling,
 * below the previous object, or on the previous object's row. Horizontally: right of a `leftAnchor`
 * sibling, centered/right-aligned via `align` ("n"/"ne"), to the right of the previous object, or at
 * an explicit `left` offset inside its parent skin.
 *
 * Size and offsets come from the active skin where it defines them, falling back to what the server
 * sent. [content] is invoked once per object with the skin child that styles it (if any).
 */
@Composable
fun DialogObjectLayout(
    dataObjects: List<DialogObject>,
    modifier: Modifier = Modifier,
    content: @Composable (data: DialogObject, skinObject: SkinObject?) -> Unit,
) {
    val skin = LocalSkin.current
    // A `Skin` object names a skin entry and lists the ids it "controls". For each controlled id,
    // record the skin child that styles/sizes it (skinObjects) and the id of the Skin object that
    // contains it (parentSkins) - the container that objects with an explicit top/left offset are
    // positioned relative to.
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
    // Some progress bars (e.g. concentration) arrive without a Skin element, so the loop above never
    // gives them a skin child and they would fall back to default colors. The server names a bar's
    // skin "<id>Bar" with a child keyed by the id (e.g. staminaBar -> stamina), so reconstruct that
    // convention to let such a bar be skinned too. It has no parent skin, so its own top/left
    // position it - the skin entry should carry colors only, not offsets.
    dataObjects.forEach { data ->
        if (data is DialogObject.ProgressBar && skinObjects.getIgnoringCase(data.id) == null) {
            skin
                .getIgnoringCase("${data.id}Bar")
                ?.children
                ?.getIgnoringCase(data.id)
                ?.let { skinObjects[data.id] = it }
        }
    }

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            // Emit each object, paired with the skin child that styles it (matched by id).
            dataObjects.forEach { data ->
                content(data, skinObjects.getIgnoringCase(data.id))
            }
        },
    ) { measurables, constraints ->
        // The reference size a Percent distance is resolved against: the bounded size when we have
        // one, otherwise the minimum we are required to fill. Progress bars default to 16dp tall
        // when no height is given.
        val widthBasis =
            if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val heightBasis =
            if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
        val progressBarHeightPx = 16.dp.roundToPx()

        val itemInfos =
            dataObjects.mapIndexed { index, data ->
                val skinObject = skinObjects.getIgnoringCase(data.id)
                val imageData = (data as? DialogObject.Image)?.name?.let { skin.getIgnoringCase(it) }

                // Size preference: an explicit pixel size from the image or skin entry, else the
                // size the server sent. A null target leaves the dimension unconstrained so the
                // child sizes itself / fills the available space (a progress bar still gets 16dp).
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

                // Offsets prefer the skin's pixel top/left over the server's. Both margins are
                // measured against widthBasis; Pixels ignore the basis, so this only affects Percent
                // offsets.
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

        // Resolve each item's top-left. Walking in placementOrder guarantees the parent skin, the
        // anchors and the data-order predecessor read below are already in `placements`.
        val placements = HashMap<String, ItemPlacement>(itemInfos.size)

        for (index in placementOrder) {
            val info = itemInfos[index]
            val data = info.data
            // The containing skin (for explicit offsets) and the previous object in data order (for
            // the "flow after the previous object" rules).
            val parentSkinPlacement = info.parentSkinId?.let { placements[it] }
            val lastPlacement = if (index > 0) placements[itemInfos[index - 1].data.id] else null

            val y =
                when {
                    // Stacked under the bottom of the sibling we anchor to.
                    data.topAnchor != null -> {
                        (placements[data.topAnchor]?.bottom ?: 0) + info.topMargin
                    }

                    // Explicit top offset, measured from the top of the parent skin container.
                    info.dataTop != null -> {
                        (parentSkinPlacement?.y ?: 0) + info.topMargin
                    }

                    // Anchored horizontally only: share that sibling's row.
                    data.leftAnchor != null -> {
                        placements[data.leftAnchor]?.y ?: 0
                    }

                    // A left offset but no vertical hint: drop below the previous object.
                    info.dataLeft != null -> {
                        lastPlacement?.bottom ?: 0
                    }

                    // No vertical hint at all: stay on the previous object's row.
                    else -> {
                        lastPlacement?.y ?: 0
                    }
                }

            val x =
                if (data.leftAnchor != null) {
                    // Just past the right edge of the sibling we anchor to.
                    (placements[data.leftAnchor]?.right ?: 0) + info.leftMargin
                } else {
                    when (data.align) {
                        // Centered across the panel width.
                        "n" -> {
                            (widthBasis - info.placeable.width) / 2 + info.leftMargin
                        }

                        // Pinned to the right edge.
                        "ne" -> {
                            widthBasis - info.placeable.width - info.leftMargin
                        }

                        else -> {
                            if (info.dataLeft == null) {
                                // No left offset: flow to the right of the previous object.
                                (lastPlacement?.right ?: 0) + info.leftMargin
                            } else {
                                // Explicit left offset, measured from the parent skin's left edge.
                                (parentSkinPlacement?.x ?: 0) + info.leftMargin
                            }
                        }
                    }
                }

            placements[data.id] = ItemPlacement(x = x, y = y, placeable = info.placeable)
        }

        // When unbounded, grow to wrap the placed content; otherwise fill the given constraints.
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
            // Place in data order (not placementOrder) so later objects draw on top of earlier ones.
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

/**
 * Resolves a dialog distance to pixels: a [DataDistance.Percent] is a fraction of [basis], while a
 * [DataDistance.Pixels] is treated as a dp count (and so ignores [basis]).
 */
private fun DataDistance.toPx(
    basis: Int,
    density: Density,
): Int =
    when (this) {
        is DataDistance.Percent -> basis * value.value / 100
        is DataDistance.Pixels -> with(density) { value.dp.roundToPx() }
    }
