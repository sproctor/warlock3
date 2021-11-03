package cc.warlock.warlock3.core.text

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class StyleRepository(
    private val caseSensitiveStyles: Flow<Map<String, Map<String, StyleDefinition>>>,
    val saveStyle: (characterId: String, key: String, style: StyleDefinition) -> Unit,
) {
    fun getStyleMap(characterId: String): Flow<Map<String, StyleDefinition>> {
        return caseSensitiveStyles.map { allStyles ->
            TreeMap<String, StyleDefinition>(String.CASE_INSENSITIVE_ORDER).apply {
                putAll(defaultStyles)
                allStyles[characterId]?.let { putAll(it) }
            }
        }
    }

    private val defaultStyles =
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
            "mono" to StyleDefinition(monospace = true),
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
}
