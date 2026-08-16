package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.DEFAULT_PANEL_SCALE
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.util.getIgnoringCase

/**
 * Lays out the objects of a game panel (progress bars, labels, links, images, ...).
 *
 * A panel rarely gives absolute coordinates for everything; each object is positioned by one of a
 * handful of rules. Vertically (highest priority first): below a sibling it `topAnchor`s to, at the
 * panel edge its `align` anchors it to, at an explicit `top` offset inside its parent skin
 * container, on the row of a `leftAnchor` sibling, below the previous object, or on the previous
 * object's row. Horizontally: right of a `leftAnchor` sibling, at the panel edge its `align` anchors
 * it to, to the right of the previous object, or at an explicit `left` offset inside its parent
 * skin.
 *
 * Size and offsets come from the active skin where it defines them, falling back to what the server
 * sent. [scale] multiplies those pixel distances (see `toPx`), leaving percentage ones alone.
 * [content] is invoked once per object with the skin child that styles it (if any).
 */
@Composable
fun PanelObjectLayout(
    dataObjects: List<PanelObject>,
    modifier: Modifier = Modifier,
    scale: Float = DEFAULT_PANEL_SCALE,
    content: @Composable (data: PanelObject, skinObject: SkinObject?) -> Unit,
) {
    val skin = LocalSkin.current
    // A `Skin` object names a skin entry and lists the ids it "controls". For each controlled id,
    // record the skin child that styles/sizes it (skinObjects) and the id of the Skin object that
    // contains it (parentSkins) - the container that objects with an explicit top/left offset are
    // positioned relative to.
    val skinObjects = mutableMapOf<String, SkinObject>()
    val parentSkins = mutableMapOf<String, String>()
    dataObjects.forEach { data ->
        if (data is PanelObject.Skin) {
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
        if (data is PanelObject.ProgressBar && skinObjects.getIgnoringCase(data.id) == null) {
            skin
                .getIgnoringCase("${data.id}Bar")
                ?.children
                ?.getIgnoringCase(data.id)
                // Colors only: these bars have no parent skin container, so a top/left in the entry
                // would resolve against nothing and pin them to (0,0). Let the server position them.
                ?.let { skinObjects[data.id] = it.copy(top = null, left = null) }
        }
    }

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            // Emit each object, paired with the skin child that styles it (matched by id). Keyed by
            // id so a widget's UI state follows the widget rather than its position: without it a
            // widget that changes place hands its state (a dropdown's selection, a checkbox's tick)
            // to whatever now occupies the slot. Ids are unique here - the panel state keys on them.
            dataObjects.forEach { data ->
                key(data.id) {
                    content(data, skinObjects.getIgnoringCase(data.id))
                }
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
                val imageName =
                    when (data) {
                        is PanelObject.Image -> data.name
                        is PanelObject.MenuImage -> data.name
                        else -> null
                    }
                val imageData = imageName?.let { skin.getIgnoringCase(it) }

                // Size preference: an explicit pixel size from the image or skin entry, else the
                // size the server sent. A null target leaves the dimension unconstrained so the
                // child sizes itself / fills the available space (a progress bar still gets 16dp).
                val widthSource =
                    (imageData?.width ?: skinObject?.width)?.let { DataDistance.Pixels(it) } ?: data.width
                val heightSource =
                    (imageData?.height ?: skinObject?.height)?.let { DataDistance.Pixels(it) } ?: data.height

                val targetWidth = widthSource?.toPx(widthBasis, this, scale)
                val targetHeight =
                    heightSource?.toPx(heightBasis, this, scale)
                        ?: progressBarHeightPx.takeIf { data is PanelObject.ProgressBar }

                val childConstraints =
                    Constraints(
                        minWidth = targetWidth ?: 0,
                        maxWidth = targetWidth ?: constraints.maxWidth,
                        minHeight = targetHeight ?: 0,
                        maxHeight = targetHeight ?: constraints.maxHeight,
                    )

                val placeable = measurables[index].measure(childConstraints)

                // A skin child's top/left places the object inside its skin container, so it only has
                // something to say about an object the server did not anchor (the injuries panel's
                // body parts, the compass arrows). Once an object is anchored, top/left are the gap
                // from that anchor and belong to the server: UberBar stacks its vital bars that way,
                // pairing each with a `<skin controls=...>` whose child sits at 0,0, and preferring
                // the skin there would drop every `top='3'` and leave the bars touching.
                // Both margins are measured against widthBasis; Pixels ignore the basis, so this only
                // affects Percent offsets.
                val dataTop =
                    if (data.topAnchor != null) {
                        data.top
                    } else {
                        skinObject?.top?.let { DataDistance.Pixels(it) } ?: data.top
                    }
                val dataLeft =
                    if (data.leftAnchor != null) {
                        data.left
                    } else {
                        skinObject?.left?.let { DataDistance.Pixels(it) } ?: data.left
                    }

                ItemInfo(
                    data = data,
                    placeable = placeable,
                    dataTop = dataTop,
                    dataLeft = dataLeft,
                    topMargin = dataTop?.toPx(widthBasis, this, scale) ?: 0,
                    leftMargin = dataLeft?.toPx(widthBasis, this, scale) ?: 0,
                    parentSkinId = parentSkins.getIgnoringCase(data.id),
                    anchor = data.align.toPanelAnchor(),
                )
            }

        // Resolve placements in topological order so an item's anchors/parent are
        // already placed when we read them. An item may anchor to one that appears
        // later in the data list: the server is free to send them in any order.
        // Drawing still happens in data order to preserve z-ordering.
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

        // Resolve each item's top-left against a panel [verticalBasis] tall. Walking in
        // placementOrder guarantees the parent skin, the anchors and the data-order predecessor read
        // below are already in `placements`.
        fun place(verticalBasis: Int): HashMap<String, ItemPlacement> {
            val placements = HashMap<String, ItemPlacement>(itemInfos.size)

            for (index in placementOrder) {
                val info = itemInfos[index]
                val data = info.data
                // The containing skin (for explicit offsets) and the previous object in data order (for
                // the "flow after the previous object" rules).
                val parentSkinPlacement = info.parentSkinId?.let { placements[it] }
                val lastPlacement = if (index > 0) placements[itemInfos[index - 1].data.id] else null

                // A "pure flow" object (no anchors, no explicit top/left, no centering) simply follows the
                // previous object along its row. When such a row would overflow a bounded panel width, wrap
                // to the next line (matching Wrayth) rather than running off the clipped edge - e.g. the
                // row of "skin search grip activate left" links at the bottom of the combat panel.
                val flowsAfterPrevious =
                    data.topAnchor == null && info.dataTop == null && data.leftAnchor == null &&
                        info.dataLeft == null && info.anchor == null
                val wraps =
                    flowsAfterPrevious && constraints.hasBoundedWidth && lastPlacement != null &&
                        lastPlacement.x > 0 &&
                        lastPlacement.right + info.leftMargin + info.placeable.width > widthBasis

                val y =
                    when {
                        // Stacked under the bottom of the sibling we anchor to.
                        data.topAnchor != null -> {
                            (placements[data.topAnchor]?.bottom ?: 0) + info.topMargin
                        }

                        // Pinned to the panel edge `align` names, then offset by `top`.
                        info.anchor != null -> {
                            when (info.anchor.y) {
                                AnchorY.Top -> (parentSkinPlacement?.y ?: 0)
                                AnchorY.Middle -> (verticalBasis - info.placeable.height) / 2
                                AnchorY.Bottom -> verticalBasis - info.placeable.height
                            } + info.topMargin
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

                        // Wrapped to a new row: drop below the row we were flowing along.
                        wraps -> {
                            lastPlacement.bottom
                        }

                        // No vertical hint at all: stay on the previous object's row.
                        else -> {
                            lastPlacement?.y ?: 0
                        }
                    }

                val x =
                    when {
                        // Just past the right edge of the sibling we anchor to.
                        data.leftAnchor != null -> {
                            (placements[data.leftAnchor]?.right ?: 0) + info.leftMargin
                        }

                        // Pinned to the panel edge `align` names, then offset by `left`.
                        info.anchor != null -> {
                            when (info.anchor.x) {
                                AnchorX.Left -> (parentSkinPlacement?.x ?: 0)
                                AnchorX.Center -> (widthBasis - info.placeable.width) / 2
                                AnchorX.Right -> widthBasis - info.placeable.width
                            } + info.leftMargin
                        }

                        // Wrapped to a new row: start again at the panel's left edge.
                        wraps -> {
                            info.leftMargin
                        }

                        // No left offset: flow to the right of the previous object.
                        info.dataLeft == null -> {
                            (lastPlacement?.right ?: 0) + info.leftMargin
                        }

                        // Explicit left offset, measured from the parent skin's left edge.
                        else -> {
                            (parentSkinPlacement?.x ?: 0) + info.leftMargin
                        }
                    }

                placements[data.id] = ItemPlacement(x = x, y = y, placeable = info.placeable)
            }
            return placements
        }

        // True for an item a taller basis would move: one the basis positions directly, and one that
        // reads such an item's placement - through an anchor, its parent skin, or by flowing after
        // it. placementOrder puts everything an item reads before the item itself, so one pass
        // settles the whole chain.
        val movesWithBasis = BooleanArray(itemInfos.size)
        for (index in placementOrder) {
            val info = itemInfos[index]

            fun moves(id: String?): Boolean = id?.let { indexById[it] }?.let { movesWithBasis[it] } == true

            movesWithBasis[index] =
                (info.anchor != null && info.anchor.y != AnchorY.Top) ||
                moves(info.data.topAnchor) ||
                moves(info.data.leftAnchor) ||
                moves(info.parentSkinId) ||
                (info.usesLastPlacement() && index > 0 && movesWithBasis[index - 1])
        }

        // Wrayth measures a bottom or middle anchor against a panel of fixed height, and clips what
        // falls outside it. Ours scrolls instead, so when the panel has no height of its own - the
        // desktop draws it in a scrolling column - the content it ends up with stands in for that
        // height, which keeps a bottom-anchored row below the rest of the panel rather than folded
        // back over it. The basis comes only from what a taller basis would not move, so it cannot
        // chase the items measured against it, plus room for the tallest anchored item itself - a
        // panel of nothing but anchored items would otherwise measure against nothing.
        val placements =
            place(heightBasis).let { first ->
                if (movesWithBasis.none { it }) return@let first
                val flowBottom =
                    itemInfos
                        .filterIndexed { index, _ -> !movesWithBasis[index] }
                        .mapNotNull { first[it.data.id]?.bottom }
                        .maxOrNull() ?: 0
                val anchoredHeight =
                    itemInfos
                        .filterIndexed { index, _ -> movesWithBasis[index] }
                        .maxOf { it.placeable.height }
                val basis = maxOf(heightBasis, flowBottom, anchoredHeight)
                if (basis == heightBasis) first else place(basis)
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
    val data: PanelObject,
    val placeable: Placeable,
    val dataTop: DataDistance?,
    val dataLeft: DataDistance?,
    val topMargin: Int,
    val leftMargin: Int,
    val parentSkinId: String?,
    val anchor: PanelAnchor?,
) {
    // True when the placement formula will read lastPlacement (the data-order
    // predecessor's placement). Mirrors the branches in y/x computation.
    fun usesLastPlacement(): Boolean {
        val needsForY = data.topAnchor == null && anchor == null && dataTop == null && data.leftAnchor == null
        val needsForX = data.leftAnchor == null && anchor == null && dataLeft == null
        return needsForY || needsForX
    }
}

/**
 * The point of the panel an object's `align` pins it to. Wrayth reads the attribute as a compass
 * bearing and pins the object's box to that point of the panel, with `top`/`left` translating it
 * from there - always down and right, whatever the bearing: `align='e' left='30'` pushes an object
 * off the right edge and out of sight, while `align='s' top='-40'` lifts one 40px clear of the
 * bottom. All of this was read off the real client in `utils/wrayth-lab`, along with the two
 * spellings that are not bearings: `center` pins to the middle of the panel on both axes, and an
 * absent (or empty) `align` leaves the object to the flow rules.
 *
 * Getting this wrong is what hid a dialog's Save button: a button the game pins to the bottom edge,
 * as a settings dialog does, was placed by `top` alone, and the negative `top` such a button carries
 * put it above the panel rather than above its bottom edge.
 */
private data class PanelAnchor(
    val x: AnchorX,
    val y: AnchorY,
)

private enum class AnchorX { Left, Center, Right }

private enum class AnchorY { Top, Middle, Bottom }

private fun String?.toPanelAnchor(): PanelAnchor? =
    when (this?.lowercase()) {
        "n" -> PanelAnchor(AnchorX.Center, AnchorY.Top)
        "s" -> PanelAnchor(AnchorX.Center, AnchorY.Bottom)
        "e" -> PanelAnchor(AnchorX.Right, AnchorY.Middle)
        "w" -> PanelAnchor(AnchorX.Left, AnchorY.Middle)
        "ne" -> PanelAnchor(AnchorX.Right, AnchorY.Top)
        "nw" -> PanelAnchor(AnchorX.Left, AnchorY.Top)
        "se" -> PanelAnchor(AnchorX.Right, AnchorY.Bottom)
        "sw" -> PanelAnchor(AnchorX.Left, AnchorY.Bottom)
        "center" -> PanelAnchor(AnchorX.Center, AnchorY.Middle)
        else -> null
    }

/**
 * Resolves a panel distance to pixels: a [DataDistance.Percent] is a fraction of [basis], while a
 * [DataDistance.Pixels] is treated as a dp count (and so ignores [basis]).
 *
 * [scale] applies to pixel distances only. The server sizes and positions panels for Wrayth's compact
 * pixel grid, and for the small font it drew widgets with, so a box can be just wide enough there and
 * too tight here. Raising the scale buys that room back with positions, sizes and margins growing
 * together, which keeps the layout proportional. It deliberately leaves the font alone (that is the
 * panel font's job) and percentage distances alone (those already track the panel).
 */
internal fun DataDistance.toPx(
    basis: Int,
    density: Density,
    scale: Float,
): Int =
    when (this) {
        is DataDistance.Percent -> basis * value.value / 100
        is DataDistance.Pixels -> with(density) { (value * scale).dp.roundToPx() }
    }
