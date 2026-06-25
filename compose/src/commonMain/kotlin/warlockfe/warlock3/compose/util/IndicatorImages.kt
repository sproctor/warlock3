package warlockfe.warlock3.compose.util

import org.jetbrains.compose.resources.DrawableResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.allDrawableResources
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.core.util.getIgnoringCase

/** The skin's "indicator" entry for a status (its image + optional position), or null. */
fun Map<String, SkinObject>.indicatorEntry(status: String): SkinObject? = getIgnoringCase("indicator")?.children?.getIgnoringCase(status)

/** The drawable a skin "indicator" entry points at via its image `name`, or null. */
fun SkinObject?.indicatorDrawable(): DrawableResource? = this?.image?.name?.let { Res.allDrawableResources[it] }

/** Convenience: the drawable for a status, resolved through the skin's "indicator" section. */
fun Map<String, SkinObject>.indicatorDrawable(status: String): DrawableResource? = indicatorEntry(status).indicatorDrawable()
