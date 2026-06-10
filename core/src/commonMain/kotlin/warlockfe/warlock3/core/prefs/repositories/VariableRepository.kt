package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.models.VariableEntity

class VariableRepository(
    private val store: CharacterConfigStore,
) {
    fun observeCharacterVariables(characterId: String): Flow<List<VariableEntity>> =
        store.observe(characterId).map { config ->
            config.variables.map { (name, value) -> VariableEntity(characterId, name, value) }
        }

    suspend fun put(variable: VariableEntity) {
        store.mutate(variable.characterId) { current ->
            current.copy(variables = current.variables + (variable.name to variable.value))
        }
    }

    suspend fun put(
        characterId: String,
        name: String,
        value: String,
    ) {
        put(VariableEntity(characterId, name, value))
    }

    suspend fun delete(
        characterId: String,
        name: String,
    ) {
        store.mutate(characterId) { current ->
            current.copy(variables = current.variables - name)
        }
    }
}
