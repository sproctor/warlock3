package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.HighlightConfig
import warlockfe.warlock3.core.prefs.config.toConfig
import warlockfe.warlock3.core.prefs.config.toHighlight
import warlockfe.warlock3.core.prefs.models.Highlight
import kotlin.uuid.Uuid

class HighlightRepositoryImpl(
    private val store: CharacterConfigStore,
) : HighlightRepository {
    override fun observeGlobal(): Flow<List<Highlight>> = observeByCharacter(GLOBAL_CHARACTER_ID)

    override fun observeByCharacter(characterId: String): Flow<List<Highlight>> =
        store.observe(characterId).map { config -> config.highlights.map { it.toHighlight() } }

    override fun observeForCharacter(characterId: String): Flow<List<Highlight>> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            observeByCharacter(characterId)
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                (own.highlights + global.highlights).map { it.toHighlight() }
            }
        }

    override suspend fun save(
        characterId: String,
        highlight: Highlight,
    ) {
        val config = highlight.toConfig()
        store.mutate(characterId) { current ->
            current.copy(highlights = current.highlights.upsert(config))
        }
    }

    override suspend fun saveGlobal(highlight: Highlight) {
        save(GLOBAL_CHARACTER_ID, highlight)
    }

    override suspend fun deleteByPattern(
        characterId: String,
        pattern: String,
    ) {
        store.mutate(characterId) { current ->
            current.copy(highlights = current.highlights.filterNot { it.pattern == pattern })
        }
    }

    override suspend fun deleteById(id: Uuid) {
        val idString = id.toString()
        val owner =
            store.snapshot().entries.firstOrNull { (_, config) ->
                config.highlights.any { it.id == idString }
            } ?: return
        store.mutate(owner.key) { current ->
            current.copy(highlights = current.highlights.filterNot { it.id == idString })
        }
    }
}

// Mirror the database's uniqueness (primary key on id, unique index on pattern): replace any
// existing highlight that collides on either, then append the new one.
private fun List<HighlightConfig>.upsert(item: HighlightConfig): List<HighlightConfig> =
    filterNot { it.id == item.id || it.pattern == item.pattern } + item
