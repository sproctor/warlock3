package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.HighlightDao
import warlockfe.warlock3.core.prefs.mappers.toEntity
import warlockfe.warlock3.core.prefs.mappers.toHighlight
import warlockfe.warlock3.core.prefs.mappers.toStyleEntities
import warlockfe.warlock3.core.prefs.models.Highlight
import kotlin.uuid.Uuid

class HighlightRepositoryImpl(
    private val highlightDao: HighlightDao,
) : HighlightRepository {
    override fun observeGlobal(): Flow<List<Highlight>> {
        return observeByCharacter("global")
    }

    override fun observeByCharacter(characterId: String): Flow<List<Highlight>> {
        return highlightDao.observeHighlightsByCharacter(characterId)
            .map { highlights ->
                highlights.map { it.toHighlight() }
            }
    }

    override fun observeForCharacter(characterId: String): Flow<List<Highlight>> {
        return highlightDao.observeHighlightsForCharacter(characterId)
            .map { highlights -> highlights.map { it.toHighlight() } }
    }

    override suspend fun save(characterId: String, highlight: Highlight) {
        withContext(NonCancellable) {
            highlightDao.save(highlight.toEntity(characterId), highlight.toStyleEntities(highlight.id))
        }
    }

    override suspend fun saveGlobal(highlight: Highlight) {
        save("global", highlight)
    }

    override suspend fun deleteByPattern(characterId: String, pattern: String) {
        withContext(NonCancellable) {
            highlightDao.deleteByPattern(pattern = pattern, characterId = characterId)
        }
    }

    override suspend fun deleteById(id: Uuid) {
        withContext(NonCancellable) {
            highlightDao.deleteById(id)
        }
    }
}
