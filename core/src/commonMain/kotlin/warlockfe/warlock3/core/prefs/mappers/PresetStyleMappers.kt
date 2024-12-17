package warlockfe.warlock3.core.prefs.mappers

import warlockfe.warlock3.core.prefs.models.PresetStyleEntity
import warlockfe.warlock3.core.text.StyleDefinition

fun PresetStyleEntity.toStyleDefinition(): StyleDefinition {
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

fun StyleDefinition.toPresetStyleEntity(key: String, characterId: String): PresetStyleEntity {
    return PresetStyleEntity(
        presetId = key,
        characterId = characterId,
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
