package warlockfe.warlock3.core.prefs.mappers

import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.HighlightEntity
import warlockfe.warlock3.core.prefs.models.HighlightStyleEntity
import warlockfe.warlock3.core.prefs.models.PopulatedHighlight
import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.uuid.Uuid

fun PopulatedHighlight.toHighlight(): Highlight =
    Highlight(
        id = highlight.id,
        pattern = highlight.pattern,
        styles = styles.associate { it.groupNumber to it.toStyleDefinition() },
        isRegex = highlight.isRegex,
        matchPartialWord = highlight.matchPartialWord,
        ignoreCase = highlight.ignoreCase,
        sound = highlight.sound,
    )

fun HighlightStyleEntity.toStyleDefinition(): StyleDefinition =
    StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
    )
