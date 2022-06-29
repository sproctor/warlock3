package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.prefs.models.Highlight
import cc.warlock.warlock3.core.prefs.sql.Highlight as DatabaseHighlight
import cc.warlock.warlock3.core.prefs.sql.HighlightQueries
import cc.warlock.warlock3.core.prefs.sql.HighlightStyleQueries
import cc.warlock.warlock3.core.prefs.sql.HightlightStyle
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.WarlockColor
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.*

class HighlightRepository(
    private val highlightQueries: HighlightQueries,
    private val highlightStyleQueries: HighlightStyleQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeGlobal(): Flow<List<Highlight>> {
        return observeForCharacter("global")
    }

    fun observeForCharacter(characterId: String): Flow<List<Highlight>> {
        return highlightQueries.getHighlightsByCharacter(
            characterId
        ) { id: UUID,
            _: String,
            pattern: String,
            isRegex: Boolean,
            matchPartialWord: Boolean,
            ignoreCase: Boolean ->
            val styles = highlightStyleQueries.getByHighlight(
                id
            ) { _: UUID,
                groupNumber: Int,
                textColor: WarlockColor,
                backgroundColor: WarlockColor,
                entireLine: Boolean,
                bold: Boolean,
                italic: Boolean,
                underline: Boolean,
                monospace: Boolean ->
                Pair(
                    groupNumber,
                    StyleDefinition(
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        entireLine = entireLine,
                        bold = bold,
                        italic = italic,
                        underline = underline,
                        monospace = monospace,
                    )
                )
            }.executeAsList()
                .toMap()
            Highlight(
                id = id,
                pattern = pattern,
                styles = styles,
                isRegex = isRegex,
                matchPartialWord = matchPartialWord,
                ignoreCase = ignoreCase,
            )
        }
            .asFlow()
            .map {
                highlightQueries.transactionWithResult {
                    it.executeAsList()
                }
            }
    }

    suspend fun save(characterId: String, highlight: Highlight) {
        withContext(ioDispatcher) {
            highlightQueries.transaction {
                highlightQueries.save(
                    DatabaseHighlight(
                        id = highlight.id,
                        characterId = characterId,
                        pattern = highlight.pattern,
                        isRegex = highlight.isRegex,
                        matchPartialWord = highlight.matchPartialWord,
                        ignoreCase = highlight.ignoreCase,
                    )
                )
                highlightStyleQueries.deleteByHighlight(highlight.id)
                highlight.styles.forEach { entry ->
                    val style = entry.value
                    highlightStyleQueries.save(
                        HightlightStyle(
                            highlightId = highlight.id,
                            groupNumber = entry.key,
                            textColor = style.textColor,
                            backgroundColor = style.backgroundColor,
                            entireLine = style.entireLine,
                            bold = style.bold,
                            italic = style.italic,
                            underline = style.underline,
                            monospace = style.monospace,
                        )
                    )
                }
            }
        }
    }

    suspend fun saveGlobal(highlight: Highlight) {
        save("global", highlight)
    }

    suspend fun deleteByPattern(characterId: String, pattern: String) {
        withContext(ioDispatcher) {
            highlightQueries.deleteByPattern(pattern = pattern, characterId = characterId)
        }
    }
}
