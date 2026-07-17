package warlockfe.warlock3.core.text

/**
 * A color the user chose, before it is resolved to concrete RGB: either a frozen [Literal], or a
 * [SkinRef] to a named skin-palette slot that re-resolves whenever the skin changes. A palette pick is
 * stored as a ref so a user's customization follows the skin (switching to a light skin doesn't leave
 * dark-on-light wreckage); a typed hex is stored as a literal ("frozen").
 *
 * This is the editor/picker-facing value. In [StyleLayer] the ref is carried as metadata beside the
 * resolved color (see `textColorRef`/`backgroundRef` and [StyleLayer.resolveRefs]) so the render path
 * keeps dealing in plain [WarlockColor] and stays untouched.
 */
sealed interface ColorValue {
    data class Literal(
        val color: WarlockColor,
    ) : ColorValue

    data class SkinRef(
        val slot: String,
    ) : ColorValue

    /** Resolves against a skin [palette] (slot -> color); a missing slot yields [WarlockColor.Unspecified]. */
    fun resolve(palette: Map<String, WarlockColor>): WarlockColor =
        when (this) {
            is Literal -> color
            is SkinRef -> palette[slot] ?: WarlockColor.Unspecified
        }
}
