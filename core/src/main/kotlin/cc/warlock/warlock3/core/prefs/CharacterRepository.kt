package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.sql.Character
import cc.warlock.warlock3.core.prefs.sql.CharacterQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CharacterRepository(
    private val characterQueries: CharacterQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observeAllCharacters(): Flow<List<GameCharacter>> {
        return characterQueries.getAll()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { dbCharacters ->
                dbCharacters.map {
                    GameCharacter(
                        accountId = it.accountId,
                        id = it.id,
                        gameCode = it.gameCode,
                        name = it.name
                    )
                }
            }
    }

    suspend fun getCharacter(id: String): GameCharacter? {
        return withContext(ioDispatcher) {
            characterQueries.getById(id).executeAsOneOrNull()?.let {
                GameCharacter(accountId = it.accountId, id = it.id, gameCode = it.gameCode, name = it.name)
            }
        }
    }

    suspend fun saveCharacter(character: GameCharacter) {
        withContext(ioDispatcher) {
            characterQueries.save(
                Character(
                    accountId = character.accountId,
                    id = character.id,
                    gameCode = character.gameCode,
                    name = character.name
                )
            )
        }
    }
}
