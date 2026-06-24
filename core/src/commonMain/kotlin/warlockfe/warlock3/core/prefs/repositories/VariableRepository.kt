package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.util.getIgnoringCase

class VariableRepository(
    private val store: CharacterConfigStore,
) {
    fun observeCharacterVariables(characterId: String): Flow<List<VariableEntity>> =
        store.observe(characterId).map { config ->
            config.variables.map { (name, value) -> VariableEntity(characterId, name, value) }
        }

    /**
     * Reads every variable for a character straight from the source of truth (the
     * [CharacterConfigStore]), suspending on its current state rather than a derived/cached snapshot.
     * Scripts read through here so a lookup right as the script starts can't race an out-of-date copy.
     */
    suspend fun getVariables(characterId: String): Map<String, String> =
        store
            .observe(characterId)
            .first()
            .variables

    /**
     * Reads a single variable from the source of truth. Variable names are matched case-insensitively,
     * matching how they are looked up in scripts.
     */
    suspend fun getVariable(
        characterId: String,
        name: String,
    ): String? = getVariables(characterId).getIgnoringCase(name)

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
