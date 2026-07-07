package warlockfe.warlock3.core.text

import kotlinx.serialization.Serializable

/**
 * A user-selectable font: the typeface [family] (a system font name, or one of the generic families
 * like "Monospace"; null means the platform/theme default), the point [size] (null = inherit), and
 * the numeric [weight] (100-900; null = the family default). Used both for the per-character default
 * and monospace fonts and for the per-window font overrides. Serialized as an inline TOML table so a
 * font reads as `{ family = "Menlo", size = 13, weight = 400 }`.
 */
@Serializable
data class FontConfig(
    val family: String? = null,
    val size: Float? = null,
    val weight: Int? = null,
    @Serializable(WarlockColorAsHexSerializer::class)
    val textColor: WarlockColor = WarlockColor.Unspecified,
) {
    /** True when nothing is set, i.e. this override contributes nothing and can be dropped. */
    fun isEmpty(): Boolean = family == null && size == null && weight == null && textColor.isUnspecified()
}
