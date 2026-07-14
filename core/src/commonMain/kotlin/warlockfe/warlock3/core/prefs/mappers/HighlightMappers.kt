package warlockfe.warlock3.core.prefs.mappers

import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.HighlightEntity
import warlockfe.warlock3.core.prefs.models.HighlightStyleEntity
import warlockfe.warlock3.core.prefs.models.PopulatedHighlight
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.text.toBackground
import kotlin.uuid.Uuid

fun PopulatedHighlight.toHighlight(): Highlight =
    Highlight(
        id = highlight.id,
        pattern = highlight.pattern,
        styles = styles.associate { it.groupNumber to it.toStyleLayer() },
        isRegex = highlight.isRegex,
        matchPartialWord = highlight.matchPartialWord,
        ignoreCase = highlight.ignoreCase,
        sound = highlight.sound,
    )

fun HighlightStyleEntity.toStyleLayer(): StyleLayer =
    StyleLayer(
        textColor = textColor.specifiedOrNull(),
        background = backgroundColor.toBackground(),
        weight = if (bold) 700 else null,
        italic = if (italic) true else null,
        underline = if (underline) true else null,
        entireLine = if (entireLine) true else null,
    )
