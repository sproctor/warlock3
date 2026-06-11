package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.config.toCharacterEntry
import warlockfe.warlock3.core.prefs.config.toGameCharacter

/**
 * The registry of known characters, stored in `connections.toml` via [ClientConfigStore]. Mostly
 * machine-written (a character is recorded on first login), but readable and occasionally hand-edited.
 */
class CharacterRepository(
    private val store: ClientConfigStore,
) {
    fun observeAllCharacters(): Flow<List<GameCharacter>> =
        store.observeConnections().map { registry ->
            registry.characters.map { it.toGameCharacter() }
        }

    suspend fun getCharacter(id: String): GameCharacter? =
        store
            .currentConnections()
            .characters
            .firstOrNull { it.id == id }
            ?.toGameCharacter()

    suspend fun saveCharacter(character: GameCharacter) {
        val entry = character.toCharacterEntry()
        store.mutateConnections { registry ->
            registry.copy(characters = registry.characters.filterNot { it.id == entry.id } + entry)
        }
    }

    suspend fun deleteCharacter(id: String) {
        store.mutateConnections { registry ->
            registry.copy(characters = registry.characters.filterNot { it.id == id })
        }
    }
}
