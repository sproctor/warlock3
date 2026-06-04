package warlockfe.warlock3.core.prefs.config

import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.uuid.Uuid

internal fun HighlightConfig.toHighlight(): Highlight =
    Highlight(
        id = id?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random(),
        pattern = pattern,
        styles = styles.associate { it.group to it.toStyleDefinition() },
        isRegex = isRegex,
        matchPartialWord = matchPartialWord,
        ignoreCase = ignoreCase,
        sound = sound,
    )

internal fun Highlight.toConfig(): HighlightConfig =
    HighlightConfig(
        id = id.toString(),
        pattern = pattern,
        isRegex = isRegex,
        matchPartialWord = matchPartialWord,
        ignoreCase = ignoreCase,
        sound = sound,
        styles = styles.map { (group, style) -> style.toStyleConfig(group) },
    )

internal fun HighlightStyleConfig.toStyleDefinition(): StyleDefinition =
    StyleDefinition(
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

internal fun StyleDefinition.toStyleConfig(group: Int): HighlightStyleConfig =
    HighlightStyleConfig(
        group = group,
        textColor = textColor,
        backgroundColor = backgroundColor,
        entireLine = entireLine,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

internal fun NameConfig.toEntity(characterId: String): NameEntity =
    NameEntity(
        id = id?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.random(),
        characterId = characterId,
        text = text,
        textColor = textColor,
        backgroundColor = backgroundColor,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        sound = sound,
    )

internal fun NameEntity.toConfig(): NameConfig =
    NameConfig(
        id = id.toString(),
        text = text,
        sound = sound,
        textColor = textColor,
        backgroundColor = backgroundColor,
        bold = bold,
        italic = italic,
        underline = underline,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )
