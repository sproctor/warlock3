package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.AlterationConfig
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.toConfig
import warlockfe.warlock3.core.prefs.config.toEntity
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import kotlin.uuid.Uuid

class AlterationRepository(
    private val store: CharacterConfigStore,
) {
    fun observeByCharacter(characterId: String): Flow<List<AlterationEntity>> =
        store.observe(characterId).map { config -> config.alterations.map { it.toEntity(characterId) } }

    fun observeForCharacter(characterId: String): Flow<List<AlterationEntity>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            observeByCharacter(characterId)
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                own.alterations.map { it.toEntity(characterId) } +
                    global.alterations.map { it.toEntity(GLOBAL_CHARACTER_ID) }
            }
        }

    suspend fun save(alteration: AlterationEntity) {
        val config = alteration.toConfig()
        store.mutate(alteration.characterId) { current ->
            current.copy(alterations = current.alterations.upsert(config))
        }
    }

    suspend fun deleteById(id: Uuid) {
        val idString = id.toString()
        val owner =
            store.snapshot().entries.firstOrNull { (_, config) ->
                config.alterations.any { it.id == idString }
            } ?: return
        store.mutate(owner.key) { current ->
            current.copy(alterations = current.alterations.filterNot { it.id == idString })
        }
    }
}

// Replace a matching alteration in place (keep its position on edit), dropping any other entry that
// collides on id; a brand-new alteration is appended.
private fun List<AlterationConfig>.upsert(item: AlterationConfig): List<AlterationConfig> {
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
