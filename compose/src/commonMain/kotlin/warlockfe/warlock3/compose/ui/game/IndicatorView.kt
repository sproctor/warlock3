package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.model.SkinImage
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.indicatorDrawable
import warlockfe.warlock3.compose.util.indicatorEntry
import warlockfe.warlock3.compose.util.indicatorIconModifier
import warlockfe.warlock3.compose.util.indicatorReference
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The base colors an [IndicatorView] resolves its per-slot accents from. The grouping and the
 * which-status-takes-which-color logic stay shared; each platform supplies its own palette - desktop
 * from the skin's `gameChrome` section, mobile from the Material color scheme.
 */
@Immutable
data class IndicatorPalette(
    /** An inactive (empty) slot. */
    val inactiveBackground: Color,
    val inactiveBorder: Color,
    // "Lit" accent: postures and concealment (invisible/hidden/webbed) and stunned.
    val litBackground: Color,
    val litBorder: Color,
    val litIcon: Color,
    // Danger accent: afflictions (poisoned/diseased), bleeding and dead.
    val dangerBackground: Color,
    val dangerBorder: Color,
    val dangerIcon: Color,
    /** Icon tint for "dead" - a neutral, high-contrast color over the danger slot. */
    val deadIcon: Color,
    // "Joined" group accent.
    val joinedBackground: Color,
    val joinedBorder: Color,
    val joinedIcon: Color,
)

/** Fallback slot size used only when the caller does not constrain [IndicatorView]'s height. */
private val DEFAULT_SLOT_SIZE = 40.dp

/**
 * The grouped status slots of the control bar, laid out as a 3-column, 2-row grid (the six status
 * groups). A lone active status fills its box (or sits at its skin-defined position); when several
 * statuses are co-active in one slot they are shrunk and offset into a packed grid so all are visible
 * instead of stacking on top of each other. The slot's highest-priority active status colors the box
 * (a "lit" accent for posture/concealment, a danger accent for health) and each icon keeps its own
 * accent tint; an inactive slot is an empty bordered box. Colors come from [palette].
 */
@Composable
fun IndicatorView(
    indicators: Set<String>,
    palette: IndicatorPalette,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    // Grouping and priority stay in code; the per-status image and position come from the skin's
    // "indicator" section. Affliction is listed before posture so the box takes the danger color (and
    // poisoned/diseased are never dropped) even though a posture is almost always present too.
    val groups: List<List<String>> =
        listOf(
            listOf("kneeling", "prone", "sitting", "standing"),
            listOf("poisoned", "diseased"),
            listOf("joined"),
            listOf("bleeding", "dead"),
            listOf("invisible", "hidden", "webbed"),
            listOf("stunned"),
        )

    val skin = LocalSkin.current
    val reference = skin.indicatorReference()
    val spacing = 4.dp
    val columns = 3
    val rows = groups.chunked(columns)
    BoxWithConstraints(modifier = modifier) {
        // Size every slot so the whole grid - all rows plus the gaps between them - fits the height the
        // caller gives us (e.g. the control bar's content height); the width then follows from that.
        val slotSize =
            if (constraints.hasBoundedHeight) {
                ((maxHeight - spacing * (rows.size - 1)) / rows.size).coerceAtLeast(0.dp)
            } else {
                DEFAULT_SLOT_SIZE
            }
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { group ->
                        IndicatorSlot(
                            group = group,
                            indicators = indicators,
                            palette = palette,
                            skin = skin,
                            reference = reference,
                            indicatorSize = slotSize,
                            shape = shape,
                        )
                    }
                }
            }
        }
    }
}

/** One slot of the [IndicatorView] grid: an accent-colored box with every active status's icon. */
@Composable
private fun IndicatorSlot(
    group: List<String>,
    indicators: Set<String>,
    palette: IndicatorPalette,
    skin: Map<String, SkinObject>,
    reference: Int,
    indicatorSize: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val active = group.filter { indicators.contains(it) }
    val accent = active.firstOrNull()?.let { indicatorAccent(it, palette) } ?: inactiveAccent(palette)
    Box(
        modifier =
            modifier
                .size(indicatorSize)
                .clip(shape)
                .background(accent.background, shape)
                .border(width = Dp.Hairline, color = accent.border, shape = shape),
    ) {
        // Pre-resolve to the statuses that actually have an icon so the packed grid has no gaps.
        val icons =
            active.mapNotNull { status ->
                val entry = skin.indicatorEntry(status) ?: return@mapNotNull null
                val drawable = entry.indicatorDrawable() ?: return@mapNotNull null
                Triple(status, entry, drawable)
            }
        icons.forEachIndexed { index, (status, entry, drawable) ->
            Image(
                modifier =
                    if (icons.size == 1) {
                        // A lone status keeps its skin placement (fills the box, or its skin offset).
                        indicatorIconModifier(entry, indicatorSize, reference)
                    } else {
                        // Co-active statuses are shrunk and offset into a grid so none is hidden.
                        packedIconModifier(index, icons.size, indicatorSize)
                    },
                painter = painterResource(drawable),
                colorFilter = indicatorAccent(status, palette).iconTint?.let { ColorFilter.tint(it) },
                contentDescription = status,
            )
        }
    }
}

/**
 * Places icon [index] of [count] co-active statuses in a packed square grid within a [boxSize] slot,
 * shrinking and offsetting each one so they are all visible instead of stacking on top of one another.
 */
private fun packedIconModifier(
    index: Int,
    count: Int,
    boxSize: Dp,
): Modifier {
    val columns = ceil(sqrt(count.toDouble())).toInt()
    val rows = ceil(count.toDouble() / columns).toInt()
    val cell = boxSize / columns
    val inset = cell * 0.1f
    // Center the (possibly partial) grid within the box.
    val xBase = (boxSize - cell * columns) / 2
    val yBase = (boxSize - cell * rows) / 2
    return Modifier
        .offset(
            x = xBase + cell * (index % columns) + inset,
            y = yBase + cell * (index / columns) + inset,
        ).size(cell - inset * 2)
}

/** Border + fill for an indicator slot, plus the tint for an icon (null keeps the asset's own colors). */
private data class IndicatorAccent(
    val border: Color,
    val background: Color,
    val iconTint: Color?,
)

private fun inactiveAccent(palette: IndicatorPalette) =
    IndicatorAccent(
        border = palette.inactiveBorder,
        background = palette.inactiveBackground,
        iconTint = null,
    )

private fun indicatorAccent(
    key: String,
    palette: IndicatorPalette,
): IndicatorAccent =
    when (key) {
        // Afflictions use full-color assets, so leave them untinted on the danger slot.
        "poisoned", "diseased" -> {
            IndicatorAccent(
                border = palette.dangerBorder,
                background = palette.dangerBackground,
                iconTint = null,
            )
        }

        "bleeding" -> {
            IndicatorAccent(
                border = palette.dangerBorder,
                background = palette.dangerBackground,
                iconTint = palette.dangerIcon,
            )
        }

        "dead" -> {
            IndicatorAccent(
                border = palette.dangerBorder,
                background = palette.dangerBackground,
                iconTint = palette.deadIcon,
            )
        }

        "joined" -> {
            IndicatorAccent(
                border = palette.joinedBorder,
                background = palette.joinedBackground,
                iconTint = palette.joinedIcon,
            )
        }

        // hidden has a full-color asset; leave it untinted on the lit slot.
        "hidden" -> {
            IndicatorAccent(
                border = palette.litBorder,
                background = palette.litBackground,
                iconTint = null,
            )
        }

        // postures, invisible, webbed, stunned
        else -> {
            IndicatorAccent(
                border = palette.litBorder,
                background = palette.litBackground,
                iconTint = palette.litIcon,
            )
        }
    }

@Preview
@Composable
private fun IndicatorPreview() {
    val previewStatuses = listOf("standing", "joined", "bleeding", "webbed", "stunned", "hidden", "diseased", "poisoned")
    val skin =
        mapOf(
            "indicator" to
                SkinObject(
                    children = previewStatuses.associateWith { SkinObject(image = SkinImage(name = it)) },
                ),
        )
    val palette =
        IndicatorPalette(
            inactiveBackground = Color(0xFF2B2B30),
            inactiveBorder = Color(0xFF3C3C42),
            litBackground = Color(0xFF4A3B12),
            litBorder = Color(0xFFB58A2E),
            litIcon = Color(0xFFF0C24B),
            dangerBackground = Color(0xFF4A1416),
            dangerBorder = Color(0xFFB5403F),
            dangerIcon = Color(0xFFF26461),
            deadIcon = Color(0xFFECECEC),
            joinedBackground = Color(0x383B5BCC),
            joinedBorder = Color(0xFF5B6BCC),
            joinedIcon = Color(0xFFAAB4F0),
        )
    CompositionLocalProvider(LocalSkin provides skin) {
        IndicatorView(
            indicators = previewStatuses.toSet(),
            palette = palette,
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier.height(88.dp),
        )
    }
}
