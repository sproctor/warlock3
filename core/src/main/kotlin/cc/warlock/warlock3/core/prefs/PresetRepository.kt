package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.prefs.sql.PresetStyle
import cc.warlock.warlock3.core.prefs.sql.PresetStyleQueries
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.WarlockColor
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PresetRepository(
    private val presetStyleQueries: PresetStyleQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observePresetsForCharacter(characterId: String): Flow<Map<String, StyleDefinition>> {
        return presetStyleQueries.getByCharacter(
            characterId = characterId
        ) { presetId: String,
            _: String,
            textColor: WarlockColor,
            backgroundColor: WarlockColor,
            entireLine: Boolean,
            bold: Boolean,
            italic: Boolean,
            underline: Boolean,
            fontFamily: String? ->
            Pair(
                presetId, StyleDefinition(
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    entireLine = entireLine,
                    bold = bold,
                    italic = italic,
                    underline = underline,
                    fontFamily = null, // FIXME
                )
            )
        }.asFlow()
            .mapToList(ioDispatcher)
            .map { defaultStyles + it.toMap() }
    }

    suspend fun save(characterId: String, key: String, style: StyleDefinition) {
        withContext(ioDispatcher) {
            presetStyleQueries.save(
                PresetStyle(
                    presetId = key,
                    characterId = characterId,
                    textColor = style.textColor,
                    backgroundColor = style.backgroundColor,
                    entireLine = style.entireLine,
                    bold = style.bold,
                    italic = style.italic,
                    underline = style.underline,
                    fontFamily = style.fontFamily,
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
        "mono" to StyleDefinition(fontFamily = "Monospace"),
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