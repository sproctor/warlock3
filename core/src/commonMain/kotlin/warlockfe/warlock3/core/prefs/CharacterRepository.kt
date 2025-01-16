package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.dao.CharacterDao
import warlockfe.warlock3.core.prefs.mappers.toEntity
import warlockfe.warlock3.core.prefs.mappers.toGameCharacter

class CharacterRepository(
    private val characterDao: CharacterDao,
) {
    fun observeAllCharacters(): Flow<List<GameCharacter>> {
        return characterDao.observeAll()
            .map { dbCharacters ->
                dbCharacters.map {
                    it.toGameCharacter()
                }
            }
    }

    suspend fun getCharacter(id: String): GameCharacter? {
        return characterDao.getById(id)?.toGameCharacter()
    }

    suspend fun saveCharacter(character: GameCharacter) {
        withContext(NonCancellable) {
            characterDao.save(character.toEntity())
        }
    }

    suspend fun deleteCharacter(id: String) {
        withContext(NonCancellable) {
            characterDao.delete(id)
        }
    }
}
