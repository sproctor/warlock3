package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.prefs.models.NameEntity
import kotlin.uuid.Uuid

interface NameRepository {
    fun observeGlobal(): Flow<List<NameConfig>>

    fun observeByCharacter(characterId: String): Flow<List<NameConfig>>

    fun observeForCharacter(characterId: String): Flow<List<NameConfig>>

    suspend fun save(
        characterId: String,
        name: NameConfig,
    )

    suspend fun deleteByText(
        characterId: String,
        text: String,
    )

    suspend fun deleteById(id: Uuid)
}
