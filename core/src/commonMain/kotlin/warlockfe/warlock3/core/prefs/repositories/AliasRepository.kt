package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.AliasConfig
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.toConfig
import warlockfe.warlock3.core.prefs.config.toEntity
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.text.Alias
import kotlin.uuid.Uuid

class AliasRepository(
    private val store: CharacterConfigStore,
) {
    fun observeForCharacter(characterId: String): Flow<List<Alias>> =
        observeConfigs(characterId).map { configs ->
            configs.mapNotNull {
                try {
                    Alias(it.pattern, it.replacement)
                } catch (_: Exception) {
                    null // TODO: Find a way to notify the user
                }
            }
        }

    fun observeByCharacter(characterId: String): Flow<List<AliasEntity>> =
        store.observe(characterId).map { config -> config.aliases.map { it.toEntity(characterId) } }

    suspend fun save(alias: AliasEntity) {
        val config = alias.toConfig()
        store.mutate(alias.characterId) { current ->
            current.copy(aliases = current.aliases.upsert(config))
        }
    }

    suspend fun deleteById(id: Uuid) {
        val idString = id.toString()
        val owner =
            store.snapshot().entries.firstOrNull { (_, config) ->
                config.aliases.any { it.id == idString }
            } ?: return
        store.mutate(owner.key) { current ->
            current.copy(aliases = current.aliases.filterNot { it.id == idString })
        }
    }

    private fun observeConfigs(characterId: String): Flow<List<AliasConfig>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            store.observe(characterId).map { it.aliases }
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                own.aliases + global.aliases
            }
        }
}

// Mirror the old table's uniqueness (id primary key): replace a matching alias in place (so an edit
// keeps its position) and drop any other entry colliding on id or pattern; a new one is appended.
private fun List<AliasConfig>.upsert(item: AliasConfig): List<AliasConfig> {
    val existingIndex = indexOfFirst { it.id == item.id || it.pattern == item.pattern }
    if (existingIndex < 0) return this + item
    return mapIndexedNotNull { index, existing ->
        when {
            index == existingIndex -> item
            existing.id == item.id || existing.pattern == item.pattern -> null
            else -> existing
        }
    }
}
