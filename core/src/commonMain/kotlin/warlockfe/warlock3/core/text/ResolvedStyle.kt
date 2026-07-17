package warlockfe.warlock3.core.text

/**
 * The fully-resolved style for a single item, produced by [resolve]. Dense: every attribute has a
 * concrete value ([WarlockColor.Unspecified] / [Background.Unset] / null when nothing set it).
 */
data class ResolvedStyle(
    val textColor: WarlockColor = WarlockColor.Unspecified,
    val background: Background = Background.Unset,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val weight: Int? = null,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val entireLine: Boolean = false,
    val monospace: Boolean = false,
)

/**
 * Resolves a stack of [StyleLayer]s, most-specific first (e.g. character, then global, then skin), by
 * taking the first layer that sets each attribute independently. Because [StyleDefinition.toLayer]
 * leaves "off" booleans null, first-set-wins matches the legacy OR merge for existing data, while also
 * letting a more-specific layer explicitly override a lower one (e.g. turn italic back off).
 */
fun resolve(layers: List<StyleLayer>): ResolvedStyle =
    ResolvedStyle(
        textColor = layers.firstNotNullOfOrNull { it.textColor } ?: WarlockColor.Unspecified,
        background = layers.map { it.background }.firstOrNull { it != Background.Unset } ?: Background.Unset,
        fontFamily = layers.firstNotNullOfOrNull { it.fontFamily },
        fontSize = layers.firstNotNullOfOrNull { it.fontSize },
        weight = layers.firstNotNullOfOrNull { it.weight },
        italic = layers.firstNotNullOfOrNull { it.italic } ?: false,
        underline = layers.firstNotNullOfOrNull { it.underline } ?: false,
        entireLine = layers.firstNotNullOfOrNull { it.entireLine } ?: false,
        monospace = layers.firstNotNullOfOrNull { it.monospace } ?: false,
    )

/**
 * Best-effort projection back to the legacy [StyleDefinition] for code paths not yet migrated. Lossy:
 * per-item [fontFamily]/[fontSize] are dropped (StyleDefinition can't hold them), [weight] collapses to
 * `bold = weight >= 600`, and [Background.None] can't be expressed so it becomes unspecified.
 */
fun ResolvedStyle.toStyleDefinition(): StyleDefinition =
    StyleDefinition(
        textColor = textColor,
        backgroundColor = background.toWarlockColor(),
        entireLine = entireLine,
        bold = (weight ?: 0) >= 600,
        italic = italic,
        underline = underline,
        monospace = monospace,
    )

/** The font half of a resolved style (family/size/weight), or null when it sets none. */
fun ResolvedStyle.toFontConfig(): FontConfig? =
    FontConfig(family = fontFamily, size = fontSize, weight = weight).takeUnless {
        it.isEmpty()
    }
