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
)

/**
 * Adapts the legacy dense [StyleDefinition] into a sparse [StyleLayer]. A `false` boolean and an
 * unspecified color both mean "inherit", so they map to null / [Background.Unset] — which makes
 * first-set-wins [resolve] reproduce the old boolean-OR merge for existing data (only explicit trues
 * participate). `bold` becomes weight 700.
 */
fun StyleDefinition.toLayer(): StyleLayer =
    StyleLayer(
        textColor = textColor.specifiedOrNull(),
        background = if (backgroundColor.isSpecified()) Background.Fill(backgroundColor) else Background.Unset,
        weight = if (bold) 700 else null,
        italic = if (italic) true else null,
        underline = if (underline) true else null,
        entireLine = if (entireLine) true else null,
        monospace = if (monospace) true else null,
    )
