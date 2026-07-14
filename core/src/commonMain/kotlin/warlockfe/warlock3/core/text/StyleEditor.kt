package warlockfe.warlock3.core.text

/** The individually-editable and individually-resettable attributes of a text style. */
enum class StyleAttribute {
    TextColor,
    Background,
    FontFamily,
    FontSize,
    Weight,
    Italic,
    Underline,
    EntireLine,
    Monospace,
}

/**
 * A single change made in the style editor. Every edit rewrites exactly one attribute of the layer being
 * edited (the character or global scope); a [Reset] clears that attribute so it inherits from the scope
 * below (global, then skin). Nothing here touches the skin layer.
 */
sealed interface StyleEdit {
    data class SetTextColor(
        val color: WarlockColor,
    ) : StyleEdit

    /** Point the text color at a skin-palette slot so it tracks the skin; the color is filled by [resolveRefs]. */
    data class SetTextColorRef(
        val slot: String,
    ) : StyleEdit

    data class SetBackground(
        val background: Background,
    ) : StyleEdit

    /** Point the background fill at a skin-palette slot so it tracks the skin; the color is filled by [resolveRefs]. */
    data class SetBackgroundRef(
        val slot: String,
    ) : StyleEdit

    data class SetFontFamily(
        val family: String?,
    ) : StyleEdit

    data class SetFontSize(
        val size: Float?,
    ) : StyleEdit

    data class SetWeight(
        val weight: Int?,
    ) : StyleEdit

    data class SetItalic(
        val italic: Boolean,
    ) : StyleEdit

    data class SetUnderline(
        val underline: Boolean,
    ) : StyleEdit

    data class SetEntireLine(
        val entireLine: Boolean,
    ) : StyleEdit

    data class SetMonospace(
        val monospace: Boolean,
    ) : StyleEdit

    data class Reset(
        val attribute: StyleAttribute,
    ) : StyleEdit
}

/**
 * Applies an [edit] to a scope's own [StyleLayer], returning the new layer to persist for that scope. A
 * concrete color/background sets the attribute; a boolean sets it explicitly (so an inherited `true` can
 * be overridden back to `false`); a null family/size/weight or a [StyleEdit.Reset] clears it to inherit.
 */
fun StyleLayer.applyEdit(edit: StyleEdit): StyleLayer =
    when (edit) {
        // A literal color clears any skin ref ("frozen"); a ref sets the slot, color filled by resolveRefs.
        is StyleEdit.SetTextColor -> copy(textColor = edit.color.specifiedOrNull(), textColorRef = null)

        is StyleEdit.SetTextColorRef -> copy(textColorRef = edit.slot)

        is StyleEdit.SetBackground -> copy(background = edit.background, backgroundRef = null)

        // The fill color is a placeholder; resolveRefs fills it from the slot (see [resolveRefs]).
        is StyleEdit.SetBackgroundRef -> copy(background = Background.Fill(WarlockColor.Unspecified), backgroundRef = edit.slot)

        is StyleEdit.SetFontFamily -> copy(fontFamily = edit.family)

        is StyleEdit.SetFontSize -> copy(fontSize = edit.size)

        is StyleEdit.SetWeight -> copy(weight = edit.weight)

        is StyleEdit.SetItalic -> copy(italic = edit.italic)

        is StyleEdit.SetUnderline -> copy(underline = edit.underline)

        is StyleEdit.SetEntireLine -> copy(entireLine = edit.entireLine)

        is StyleEdit.SetMonospace -> copy(monospace = edit.monospace)

        is StyleEdit.Reset -> reset(edit.attribute)
    }

private fun StyleLayer.reset(attribute: StyleAttribute): StyleLayer =
    when (attribute) {
        StyleAttribute.TextColor -> copy(textColor = null, textColorRef = null)
        StyleAttribute.Background -> copy(background = Background.Unset, backgroundRef = null)
        StyleAttribute.FontFamily -> copy(fontFamily = null)
        StyleAttribute.FontSize -> copy(fontSize = null)
        StyleAttribute.Weight -> copy(weight = null)
        StyleAttribute.Italic -> copy(italic = null)
        StyleAttribute.Underline -> copy(underline = null)
        StyleAttribute.EntireLine -> copy(entireLine = null)
        StyleAttribute.Monospace -> copy(monospace = null)
    }

/** The scope that supplies [attribute] in this sourced style, or null when nothing in the stack set it. */
fun SourcedStyle.sourceOf(attribute: StyleAttribute): StyleScope? =
    when (attribute) {
        StyleAttribute.TextColor -> textColor.source
        StyleAttribute.Background -> background.source
        StyleAttribute.FontFamily -> fontFamily.source
        StyleAttribute.FontSize -> fontSize.source
        StyleAttribute.Weight -> weight.source
        StyleAttribute.Italic -> italic.source
        StyleAttribute.Underline -> underline.source
        StyleAttribute.EntireLine -> entireLine.source
        StyleAttribute.Monospace -> monospace.source
    }

/** The style the editor's live sample should render: the whole [stack] resolved, ignoring source tags. */
fun sampleStyle(stack: List<Pair<StyleScope, StyleLayer>>): ResolvedStyle = resolve(stack.map { it.second })

/**
 * Everything a single-style editor needs for one item at one edit scope: the per-attribute [sourced]
 * state (for the "set here"/"inherited" tags and reset gating), the resolved [sample] for the live
 * preview, the [editScope] being written, and the [editLayer] to fold edits into before persisting.
 */
data class StyleEditorModel(
    val sourced: SourcedStyle,
    val sample: ResolvedStyle,
    val editScope: StyleScope,
    val editLayer: StyleLayer,
)

/**
 * Assembles the [StyleEditorModel] for an item from its three cascade layers. A null [characterLayer]
 * means the global scope is being edited: the character layer is left out of the cascade entirely (so
 * one character's overrides don't masquerade as "inherited" while editing the all-characters defaults)
 * and edits target the global layer. Otherwise the character layer is edited over global over skin.
 */
fun styleEditorModel(
    characterLayer: StyleLayer?,
    globalLayer: StyleLayer,
    skinLayer: StyleLayer,
): StyleEditorModel {
    val stack =
        buildList {
            if (characterLayer != null) add(StyleScope.CHARACTER to characterLayer)
            add(StyleScope.GLOBAL to globalLayer)
            add(StyleScope.SKIN to skinLayer)
        }
    return StyleEditorModel(
        sourced = resolveSourced(stack),
        sample = sampleStyle(stack),
        editScope = if (characterLayer != null) StyleScope.CHARACTER else StyleScope.GLOBAL,
        editLayer = characterLayer ?: globalLayer,
    )
}
