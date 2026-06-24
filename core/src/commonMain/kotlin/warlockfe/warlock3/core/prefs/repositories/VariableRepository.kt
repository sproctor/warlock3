package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
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
     * [CharacterConfigStore]). The store keeps the config in memory, so this is a direct read of the
     * current state, not a derived/cached snapshot a lookup at script start could race.
     */
    fun getVariables(characterId: String): Map<String, String> = store.current(characterId).variables

    /**
     * Reads a single variable from the source of truth. Variable names are matched case-insensitively,
     * matching how they are looked up in scripts.
     */
    fun getVariable(
        characterId: String,
        name: String,
    ): String? = getVariables(characterId).getIgnoringCase(name)

    suspend fun put(variable: VariableEntity) {
        store.mutate(variable.characterId) { current ->
            // Variable names are case-insensitive, so drop any existing entry that differs only in case
            // before setting the new one; otherwise case-variant duplicates accumulate and a later read
            // (which matches case-insensitively) could resolve to the wrong one.
            val deduped =
                current.variables.filterKeys { it == variable.name || !it.equals(variable.name, ignoreCase = true) }
            current.copy(variables = deduped + (variable.name to variable.value))
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
            // Remove case-insensitively so a delete matches the same entry a read would resolve.
            current.copy(variables = current.variables.filterKeys { !it.equals(name, ignoreCase = true) })
        }
    }
}
