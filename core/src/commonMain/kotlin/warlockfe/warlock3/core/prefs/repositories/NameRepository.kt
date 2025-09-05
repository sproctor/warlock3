package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.NameEntity
import java.util.*

interface NameRepository {
    fun observeGlobal(): Flow<List<NameEntity>>

    fun observeByCharacter(characterId: String): Flow<List<NameEntity>>

    fun observeForCharacter(characterId: String): Flow<List<NameEntity>>

    suspend fun save(name: NameEntity)

    suspend fun deleteByText(characterId: String, text: String)

    suspend fun deleteById(id: UUID)
}