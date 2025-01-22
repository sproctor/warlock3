package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.PresetStyleDao
import warlockfe.warlock3.core.prefs.mappers.toPresetStyleEntity
import warlockfe.warlock3.core.prefs.mappers.toStyleDefinition
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor

class PresetRepository(
    private val presetStyleQueries: PresetStyleDao,
) {
    fun observePresetsForCharacter(characterId: String): Flow<Map<String, StyleDefinition>> {
        return presetStyleQueries.observeByCharacter(
            characterId = characterId
        )
            .map { preset -> defaultStyles + preset.associate { it.presetId to it.toStyleDefinition() } }
    }

    suspend fun save(characterId: String, key: String, style: StyleDefinition) {
        withContext(NonCancellable) {
            presetStyleQueries.save(
                style.toPresetStyleEntity(
                    key = key,
                    characterId = characterId,
                )
            )
        }
    }
}

val defaultStyles =
    mapOf(
        "bold" to StyleDefinition(
            textColor = WarlockColor("#FFFF00"),
        ),
        "command" to StyleDefinition(
            textColor = WarlockColor("#FFFFFF"),
            backgroundColor = WarlockColor("#404040"),
        ),
        "default" to StyleDefinition(
            textColor = WarlockColor("#F0F0FF"),
            backgroundColor = WarlockColor("#191932"),
        ),
        "echo" to StyleDefinition(
            textColor = WarlockColor("#FFFF80"),
        ),
        "error" to StyleDefinition(
            textColor = WarlockColor(red = 0xFF, green = 0, blue = 0)
        ),
        "link" to StyleDefinition(
            textColor = WarlockColor("#ADD8E6"),
            underline = true
        ),
        "mono" to StyleDefinition(fontFamily = "monospace"),
        "roomName" to StyleDefinition(
            textColor = WarlockColor("#FFFFFF"),
            backgroundColor = WarlockColor("#0000FF"),
            entireLine = true,
        ),
        "speech" to StyleDefinition(
            textColor = WarlockColor("#80FF80"),
        ),
        "thought" to StyleDefinition(
            textColor = WarlockColor("#FF8000"),
        ),
        "watching" to StyleDefinition(
            textColor = WarlockColor("#FFFF00"),
        ),
        "whisper" to StyleDefinition(
            textColor = WarlockColor("#80FFFF"),
        ),
    )