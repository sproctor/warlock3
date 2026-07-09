package warlockfe.warlock3.core.text

/** The layers of the appearance cascade, most-specific first. */
enum class StyleScope {
    CHARACTER,
    GLOBAL,
    SKIN,
}

/** A resolved attribute value tagged with the [StyleScope] that supplied it; [source] null = nothing set it. */
data class Sourced<out T>(
    val value: T,
    val source: StyleScope?,
)

/**
 * Like [ResolvedStyle] but every attribute carries the [StyleScope] that set it, so the settings editor
 * can show each control's "set here" vs "inherited from Global/skin" state and enable a per-attribute
 * reset only when the value was set at the scope being edited. This is a presentation concern — the
 * renderer uses the plain [resolve] instead.
 */
data class SourcedStyle(
    val textColor: Sourced<WarlockColor>,
    val background: Sourced<Background>,
    val fontFamily: Sourced<String?>,
    val fontSize: Sourced<Float?>,
    val weight: Sourced<Int?>,
    val italic: Sourced<Boolean>,
    val underline: Sourced<Boolean>,
    val entireLine: Sourced<Boolean>,
    val monospace: Sourced<Boolean>,
)

/** The source-tracking twin of [resolve]. [stack] is scope-tagged, most-specific first. */
fun resolveSourced(stack: List<Pair<StyleScope, StyleLayer>>): SourcedStyle {
    fun <T : Any> first(select: (StyleLayer) -> T?): Sourced<T>? {
        for ((scope, layer) in stack) {
            val value = select(layer) ?: continue
            return Sourced(value, scope)
        }
        return null
    }
    return SourcedStyle(
        textColor = first { it.textColor } ?: Sourced(WarlockColor.Unspecified, null),
        background = first { it.background.takeIf { bg -> bg != Background.Unset } } ?: Sourced(Background.Unset, null),
        fontFamily = first { it.fontFamily }.orUnset(),
        fontSize = first { it.fontSize }.orUnset(),
        weight = first { it.weight }.orUnset(),
        italic = first { it.italic } ?: Sourced(false, null),
        underline = first { it.underline } ?: Sourced(false, null),
        entireLine = first { it.entireLine } ?: Sourced(false, null),
        monospace = first { it.monospace } ?: Sourced(false, null),
    )
}

/** Widen a "found or not" sourced value into a nullable-valued one (unset -> Sourced(null, null)). */
private fun <T> Sourced<T>?.orUnset(): Sourced<T?> = if (this != null) Sourced(value, source) else Sourced(null, null)
