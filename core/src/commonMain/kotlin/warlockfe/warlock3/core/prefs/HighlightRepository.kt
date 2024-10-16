package warlockfe.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.sql.HighlightQueries
import warlockfe.warlock3.core.prefs.sql.HighlightStyleQueries
import warlockfe.warlock3.core.prefs.sql.HighlightStyle
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.*
import warlockfe.warlock3.core.prefs.sql.Highlight as DatabaseHighlight

class HighlightRepository(
    private val highlightQueries: HighlightQueries,
    private val highlightStyleQueries: HighlightStyleQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeGlobal(): Flow<List<Highlight>> {
        return observeByCharacter("global")
    }

    fun observeByCharacter(characterId: String): Flow<List<Highlight>> {
        return highlightQueries.getHighlightsByCharacter(
            characterId
        ) { id: UUID,
            _: String,
            pattern: String,
            isRegex: Boolean,
            matchPartialWord: Boolean,
            ignoreCase: Boolean ->
            sqlToHighlight(id, pattern, isRegex, matchPartialWord, ignoreCase)
        }
            .asFlow()
            .map {
                highlightQueries.transactionWithResult {
                    it.executeAsList()
                }
            }
            .flowOn(ioDispatcher)
    }

    fun observeForCharacter(characterId: String): Flow<List<Highlight>> {
        return highlightQueries.getHighlightsForCharacter(
            characterId
        ) { id: UUID,
            _: String,
            pattern: String,
            isRegex: Boolean,
            matchPartialWord: Boolean,
            ignoreCase: Boolean ->
            sqlToHighlight(id, pattern, isRegex, matchPartialWord, ignoreCase)
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
                        HighlightStyle(
                            highlightId = highlight.id,
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

    suspend fun deleteById(id: UUID) {
        withContext(ioDispatcher) {
            highlightQueries.deleteById(id)
        }
    }

    private fun sqlToHighlight(
        id: UUID,
        pattern: String,
        isRegex: Boolean,
        matchPartialWord: Boolean,
        ignoreCase: Boolean,
    ): Highlight {
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
            fontFamily: String?,
            fontSize: Float? ->
            Pair(
                groupNumber,
                StyleDefinition(
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    entireLine = entireLine,
                    bold = bold,
                    italic = italic,
                    underline = underline,
                    fontFamily = fontFamily,
                    fontSize = fontSize,
                )
            )
        }.executeAsList()
            .toMap()
        return Highlight(
            id = id,
            pattern = pattern,
            styles = styles,
            isRegex = isRegex,
            matchPartialWord = matchPartialWord,
            ignoreCase = ignoreCase,
        )
    }
}
