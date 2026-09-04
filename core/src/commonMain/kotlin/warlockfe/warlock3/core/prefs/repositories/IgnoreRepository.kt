package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.IgnoreConfig
import warlockfe.warlock3.core.prefs.config.toConfig
import warlockfe.warlock3.core.prefs.config.toIgnore
import warlockfe.warlock3.core.prefs.models.Ignore
import kotlin.uuid.Uuid

class IgnoreRepository(
    private val store: CharacterConfigStore,
) {
    fun observeByCharacter(characterId: String): Flow<List<Ignore>> =
        store.observe(characterId).map { config -> config.ignores.map { it.toIgnore() } }

    fun observeForCharacter(characterId: String): Flow<List<Ignore>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            observeByCharacter(characterId)
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                own.ignores.map { it.toIgnore() } + global.ignores.map { it.toIgnore() }
            }
        }

    suspend fun save(
        characterId: String,
        ignore: Ignore,
    ) {
        val config = ignore.toConfig()
        store.mutate(characterId) { current ->
            current.copy(ignores = current.ignores.upsert(config))
        }
    }

    suspend fun deleteById(id: Uuid) {
        val idString = id.toString()
        val owner =
            store.snapshot().entries.firstOrNull { (_, config) ->
                config.ignores.any { it.id == idString }
            } ?: return
        store.mutate(owner.key) { current ->
            current.copy(ignores = current.ignores.filterNot { it.id == idString })
        }
    }
}

// Replace a matching ignore in place (keep its position on edit); a brand-new ignore is appended.
// Keyed on id only — unlike highlights, the same pattern with different modes is legitimately distinct.
private fun List<IgnoreConfig>.upsert(item: IgnoreConfig): List<IgnoreConfig> {
    val existingIndex = indexOfFirst { it.id == item.id }
    if (existingIndex < 0) return this + item
    return mapIndexedNotNull { index, existing ->
        when {
            index == existingIndex -> item
            existing.id == item.id -> null
            else -> existing
        }
    }
}
