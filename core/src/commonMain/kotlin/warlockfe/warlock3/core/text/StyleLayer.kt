package warlockfe.warlock3.core.text

/**
 * A sparse set of style overrides for one layer of the cascade (skin, global, or character). Every
 * attribute is optional: null / [Background.Unset] means "this layer says nothing — inherit from the
 * layer below". [resolve] stacks layers most-specific-first and takes, per attribute independently, the
 * first layer that sets it.
 *
 * Bold is not a separate flag: it is [weight] == 700. [weight] doubles as the font weight, so "bold" and
 * an explicit weight can never contradict each other through the cascade. [monospace] means "render in
 * the monospace font" (from a `<output class="mono"/>` line or a mono-flagged style); the actual mono
 * family/size/weight is resolved separately at render time, not stored here.
 */
data class StyleLayer(
    val textColor: WarlockColor? = null,
    val background: Background = Background.Unset,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val weight: Int? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val entireLine: Boolean? = null,
    val monospace: Boolean? = null,
    // Skin-palette slots the colors reference, carried beside the resolved color so a palette pick tracks
    // the skin. Null = a frozen literal. [resolveRefs] refreshes [textColor]/[background] from these; the
    // render path ignores them and reads the plain colors.
    val textColorRef: String? = null,
    val backgroundRef: String? = null,
)

/**
 * Refreshes any skin-referenced colors from the current skin [palette] (slot -> color), leaving the ref
 * metadata in place so the editor can still show a color as skin-tracked. Applied at the compose
 * boundary (render + settings) where the skin is known; a literal color (no ref) is untouched, and a ref
 * to a slot the skin doesn't define keeps its last resolved color.
 */
fun StyleLayer.resolveRefs(palette: Map<String, WarlockColor>): StyleLayer {
    if (textColorRef == null && backgroundRef == null) return this
    return copy(
        textColor = textColorRef?.let { palette[it] } ?: textColor,
        background =
            if (backgroundRef != null && background is Background.Fill) {
                palette[backgroundRef]?.let { Background.Fill(it) } ?: background
            } else {
                background
            },
    )
}

/**
 * Adapts the legacy dense [StyleDefinition] into a sparse [StyleLayer]. A `false` boolean and an
 * unspecified color both mean "inherit", so they map to null / [Background.Unset] — which makes
 * first-set-wins [resolve] reproduce the old boolean-OR merge for existing data (only explicit trues
 * participate). `bold` becomes weight 700.
 */
fun StyleDefinition.toLayer(): StyleLayer =
    StyleLayer(
        textColor = textColor.specifiedOrNull(),
        background = backgroundColor.toBackground(),
        weight = if (bold) 700 else null,
        italic = if (italic) true else null,
        underline = if (underline) true else null,
        entireLine = if (entireLine) true else null,
        monospace = if (monospace) true else null,
    )
