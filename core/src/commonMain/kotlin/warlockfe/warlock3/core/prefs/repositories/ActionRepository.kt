package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.prefs.config.ActionConfig
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.toAction
import warlockfe.warlock3.core.prefs.config.toConfig
import warlockfe.warlock3.core.prefs.models.Action
import warlockfe.warlock3.core.prefs.models.ActionBar
import kotlin.uuid.Uuid

/**
 * Reads and writes the flat per-character pool of [Action]s plus the ordered toolbar of action ids,
 * funnelling through [CharacterConfigStore] so writes never clobber other config sections.
 */
class ActionRepository(
    private val store: CharacterConfigStore,
) {
    /**
     * The resolved button bar for the game screen: the character's own actions merged with the global
     * ones, plus the ordered toolbar (this character's toolbar first, then global). Toolbar/child ids
     * that don't resolve to an action in the merged pool are skipped.
     */
    fun observeForCharacter(characterId: String): Flow<ActionBar> =
        if (characterId == GLOBAL_CHARACTER_ID) {
            store.observe(characterId).map { resolve(it.toolbar, it.actions) }
        } else {
            combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
                resolve(own.toolbar + global.toolbar, own.actions + global.actions)
            }
        }

    /** This character's own action pool (no global merge), for the settings editor. */
    fun observePool(characterId: String): Flow<List<Action>> =
        store.observe(characterId).map { config -> config.actions.map { it.toAction() } }

    /** This character's own toolbar ids (no global merge), for the settings editor. */
    fun observeToolbar(characterId: String): Flow<List<Uuid>> =
        store.observe(characterId).map { config -> config.toolbar.mapNotNull { it.toUuidOrNull() } }

    /** Insert or replace one action in the pool (matched by id; an edit keeps its position). */
    suspend fun saveAction(
        characterId: String,
        action: Action,
    ) {
        val config = action.toConfig()
        store.mutate(characterId) { current ->
            current.copy(actions = current.actions.upsert(config))
        }
    }

    /**
     * Remove an action and scrub its id from the toolbar and from every group's children, so no
     * dangling reference is left behind in this character's config.
     */
    suspend fun deleteAction(
        characterId: String,
        id: Uuid,
    ) {
        val idString = id.toString()
        store.mutate(characterId) { current ->
            current.copy(
                actions =
                    current.actions
                        .filterNot { it.id == idString }
                        .map { it.copy(children = it.children.filterNot { child -> child == idString }) },
                toolbar = current.toolbar.filterNot { it == idString },
            )
        }
    }

    /** Replace the toolbar membership and order for a character. */
    suspend fun setToolbar(
        characterId: String,
        toolbar: List<Uuid>,
    ) {
        store.mutate(characterId) { current ->
            current.copy(toolbar = toolbar.map { it.toString() })
        }
    }
}

private fun resolve(
    toolbarIds: List<String>,
    actionConfigs: List<ActionConfig>,
): ActionBar {
    val byId = actionConfigs.map { it.toAction() }.associateBy { it.id }
    val toolbar = toolbarIds.mapNotNull { it.toUuidOrNull()?.let(byId::get) }
    return ActionBar(actions = byId, toolbar = toolbar)
}

private fun String.toUuidOrNull(): Uuid? = runCatching { Uuid.parse(this) }.getOrNull()

// Replace a matching action in place (so an edit keeps its position); a new one is appended. Ids are
// unique, so matching on id alone is enough.
private fun List<ActionConfig>.upsert(item: ActionConfig): List<ActionConfig> {
    val existingIndex = indexOfFirst { it.id == item.id }
    if (existingIndex < 0) return this + item
    return mapIndexed { index, existing -> if (index == existingIndex) item else existing }
}
