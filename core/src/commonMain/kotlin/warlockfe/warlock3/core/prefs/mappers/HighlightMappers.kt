package warlockfe.warlock3.core.prefs.mappers

import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.HighlightEntity
import warlockfe.warlock3.core.prefs.models.HighlightStyleEntity
import warlockfe.warlock3.core.prefs.models.PopulatedHighlight
import warlockfe.warlock3.core.text.StyleDefinition
import java.util.*

fun Highlight.toEntity(characterId: String): HighlightEntity {
    return HighlightEntity(
        id = id,
        characterId = characterId,
        pattern = pattern,
        isRegex = isRegex,
        matchPartialWord = matchPartialWord,
        ignoreCase = ignoreCase,
    )
}

fun Highlight.toStyleEntities(highlightId: UUID): List<HighlightStyleEntity> {
    return styles.map { entry ->
        val style = entry.value
        HighlightStyleEntity(
            highlightId = highlightId,
            groupNumber = entry.key,
            textColor = style.textColor,
            backgroundColor = style.backgroundColor,
            entireLine = style.entireLine,
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            fontFamily = style.fontFamily,
            fontSize = style.fontSize,
        )
    }
}

fun PopulatedHighlight.toHighlight(): Highlight {
    return Highlight(
        id = highlight.id,
        pattern = highlight.pattern,
        styles = styles.associate { it.groupNumber to it.toStyleDefinition() },
        isRegex = highlight.isRegex,
        matchPartialWord = highlight.matchPartialWord,
        ignoreCase = highlight.ignoreCase,
    )
}

fun HighlightStyleEntity.toStyleDefinition(): StyleDefinition {
    return StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
    )
}