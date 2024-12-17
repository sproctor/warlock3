package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.mappers.toEntity
import warlockfe.warlock3.core.prefs.mappers.toHighlight
import warlockfe.warlock3.core.prefs.mappers.toStyleEntities
import warlockfe.warlock3.core.prefs.models.Highlight
import java.util.*

class HighlightRepository(
    private val highlightDao: HighlightDao,
) {
    fun observeGlobal(): Flow<List<Highlight>> {
        return observeByCharacter("global")
    }

    fun observeByCharacter(characterId: String): Flow<List<Highlight>> {
        return highlightDao.observeHighlightsByCharacter(characterId)
            .map { highlights ->
                highlights.map { it.toHighlight() }
            }
    }

    fun observeForCharacter(characterId: String): Flow<List<Highlight>> {
        return highlightDao.observeHighlightsForCharacter(characterId)
            .map { highlights -> highlights.map { it.toHighlight() } }
    }

    suspend fun save(characterId: String, highlight: Highlight) {
        withContext(NonCancellable) {
            highlightDao.save(highlight.toEntity(characterId), highlight.toStyleEntities(highlight.id))
        }
    }

    suspend fun saveGlobal(highlight: Highlight) {
        save("global", highlight)
    }

    suspend fun deleteByPattern(characterId: String, pattern: String) {
        withContext(NonCancellable) {
            highlightDao.deleteByPattern(pattern = pattern, characterId = characterId)
        }
    }

    suspend fun deleteById(id: UUID) {
        withContext(NonCancellable) {
            highlightDao.deleteById(id)
        }
    }
}
