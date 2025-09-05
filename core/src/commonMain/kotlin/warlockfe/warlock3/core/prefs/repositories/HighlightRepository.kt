package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.Highlight
import java.util.*

interface HighlightRepository {
    fun observeGlobal(): Flow<List<Highlight>>

    fun observeByCharacter(characterId: String): Flow<List<Highlight>>

    fun observeForCharacter(characterId: String): Flow<List<Highlight>>

    suspend fun save(characterId: String, highlight: Highlight)

    suspend fun saveGlobal(highlight: Highlight)

    suspend fun deleteByPattern(characterId: String, pattern: String)

    suspend fun deleteById(id: UUID)
}