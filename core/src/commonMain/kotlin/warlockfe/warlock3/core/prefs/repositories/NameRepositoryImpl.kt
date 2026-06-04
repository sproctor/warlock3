package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.prefs.config.toConfig
import warlockfe.warlock3.core.prefs.config.toEntity
import warlockfe.warlock3.core.prefs.models.NameEntity
import kotlin.uuid.Uuid

class NameRepositoryImpl(
    private val store: CharacterConfigStore,
) : NameRepository {
    override fun observeGlobal(): Flow<List<NameEntity>> = observeByCharacter(GLOBAL_CHARACTER_ID)

    override fun observeByCharacter(characterId: String): Flow<List<NameEntity>> =
        store.observe(characterId).map { config -> config.names.map { it.toEntity(characterId) } }

    override fun observeForCharacter(characterId: String): Flow<List<NameEntity>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            observeByCharacter(characterId)
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                own.names.map { it.toEntity(characterId) } +
                    global.names.map { it.toEntity(GLOBAL_CHARACTER_ID) }
            }
        }

    override suspend fun save(name: NameEntity) {
        val config = name.toConfig()
        store.mutate(name.characterId) { current ->
            current.copy(names = current.names.upsert(config))
        }
    }

    override suspend fun deleteByText(
        characterId: String,
        text: String,
    ) {
        store.mutate(characterId) { current ->
            current.copy(names = current.names.filterNot { it.text == text })
        }
    }

    override suspend fun deleteById(id: Uuid) {
        val idString = id.toString()
        val owner =
            store.snapshot().entries.firstOrNull { (_, config) ->
                config.names.any { it.id == idString }
            } ?: return
        store.mutate(owner.key) { current ->
            current.copy(names = current.names.filterNot { it.id == idString })
        }
    }
}

// Mirror the database's uniqueness (primary key on id, unique index on text).
private fun List<NameConfig>.upsert(item: NameConfig): List<NameConfig> =
    filterNot { it.id == item.id || it.text == item.text } + item
