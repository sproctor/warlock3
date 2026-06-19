package warlockfe.warlock3.compose.util

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.allDrawableResources
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.core.util.getIgnoringCase

/** The skin's "indicator" entry for a status (its image + optional position), or null. */
fun Map<String, SkinObject>.indicatorEntry(status: String): SkinObject? = getIgnoringCase("indicator")?.children?.getIgnoringCase(status)

/** The reference box size the indicator offsets are authored against (the skin's panel size; 24 by default). */
fun Map<String, SkinObject>.indicatorReference(): Int {
    val panel = getIgnoringCase("indicator")
    return panel?.width ?: panel?.height ?: 24
}

/** The drawable a skin "indicator" entry points at via its image `name`, or null. */
fun SkinObject?.indicatorDrawable(): DrawableResource? = this?.image?.name?.let { Res.allDrawableResources[it] }

/** Convenience: the drawable for a status, resolved through the skin's "indicator" section. */
fun Map<String, SkinObject>.indicatorDrawable(status: String): DrawableResource? = indicatorEntry(status).indicatorDrawable()

/**
 * Places one status icon inside its indicator box. Skin offsets ([SkinObject.top]/[left][SkinObject.left]/
 * [width][SkinObject.width]/[height][SkinObject.height]) are authored against a [reference]-unit box
 * and scaled to the actual [boxSize], so several active statuses can sit side by side. An entry with
 * no offsets fills the box (the single-status case).
 */
fun indicatorIconModifier(
    entry: SkinObject,
    boxSize: Dp,
    reference: Int,
): Modifier {
    val positioned = entry.top != null || entry.left != null || entry.width != null || entry.height != null
    if (!positioned) return Modifier.fillMaxSize().padding(boxSize / 6f)
    val scale = boxSize / reference.dp
    return Modifier
        .offset(x = ((entry.left ?: 0) * scale).dp, y = ((entry.top ?: 0) * scale).dp)
        .size(width = ((entry.width ?: reference) * scale).dp, height = ((entry.height ?: reference) * scale).dp)
}
