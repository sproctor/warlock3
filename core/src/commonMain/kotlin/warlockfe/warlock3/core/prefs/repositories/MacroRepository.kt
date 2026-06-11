package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.core.macro.Macro
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.models.MacroEntity

// TODO: Make Keyboard mappings an interface and pass it here
class MacroRepository(
    // Kept only as the legacy migration source ([exportLegacyMacros]); live reads/writes go to [store].
    private val macroDao: MacroDao,
    private val store: CharacterConfigStore,
    private val keyMap: Map<String, Long>,
    private val reverseKeyMap: Map<Long, String>,
) {
    suspend fun getGlobalCount(): Int = store.current(GLOBAL_CHARACTER_ID).macros.size

    fun observeGlobalMacros(): Flow<List<Macro>> = store.observe(GLOBAL_CHARACTER_ID).map { it.macros.toMacros() }

    fun observeCharacterMacros(characterId: String): Flow<List<Macro>> {
        require(characterId != GLOBAL_CHARACTER_ID)
        return combine(store.observe(characterId), store.observe(GLOBAL_CHARACTER_ID)) { own, global ->
            (own.macros + global.macros).toMacros()
        }
    }

    fun observeOnlyCharacterMacros(characterId: String): Flow<List<Macro>> {
        require(characterId != GLOBAL_CHARACTER_ID)
        return store.observe(characterId).map { it.macros.toMacros() }
    }

    suspend fun delete(
        characterId: String,
        keyCombo: MacroKeyCombo,
    ) {
        val keyString = keyCombo.toKeyString(reverseKeyMap)
        store.mutate(characterId) { current ->
            current.copy(macros = current.macros - keyString)
        }
    }

    suspend fun put(
        characterId: String,
        keyCombo: MacroKeyCombo,
        value: String,
    ) {
        val keyString = keyCombo.toKeyString(reverseKeyMap)
        store.mutate(characterId) { current ->
            current.copy(macros = current.macros + (keyString to value))
        }
    }

    suspend fun deleteAllGlobals() {
        store.mutate(GLOBAL_CHARACTER_ID) { current ->
            current.copy(macros = emptyMap())
        }
    }

    /**
     * Reads the character's macros out of the legacy `macro` table for the one-time TOML migration,
     * returning them as the keyString -> action map the config stores. Resolves the keyString from
     * the modern `key` column, falling back to the deprecated keyCode + modifiers for rows written
     * before that column existed (what the old `migrateMacros` repaired in place); rows whose keyCode
     * can't be mapped are dropped.
     */
    suspend fun exportLegacyMacros(characterId: String): Map<String, String> =
        macroDao
            .getByCharacter(characterId)
            .mapNotNull { entity -> entity.toKeyStringOrNull()?.let { it to entity.value } }
            .toMap()

    /** Writes a batch of macros (e.g. from a settings import) into the config store. */
    suspend fun importMacros(macros: List<MacroEntity>) {
        macros.groupBy { it.characterId }.forEach { (characterId, entries) ->
            val entriesMap = entries.mapNotNull { entity -> entity.toKeyStringOrNull()?.let { it to entity.value } }.toMap()
            if (entriesMap.isEmpty()) return@forEach
            store.mutate(characterId) { current ->
                current.copy(macros = current.macros + entriesMap)
            }
        }
    }

    private fun Map<String, String>.toMacros(): List<Macro> =
        map { (keyString, action) ->
            val parts = keyString.split(" ")
            Macro(
                keyCombo =
                    MacroKeyCombo(
                        keyCode = keyMap[parts.last()] ?: 0,
                        ctrl = parts.contains("ctrl"),
                        alt = parts.contains("alt"),
                        shift = parts.contains("shift"),
                        meta = parts.contains("meta"),
                    ),
                action = action,
            )
        }

    @Suppress("DEPRECATION")
    private fun MacroEntity.toKeyStringOrNull(): String? =
        if (key.isNotBlank()) {
            key
        } else {
            reverseKeyMap[keyCode]?.let { base ->
                buildString {
                    if (ctrl) append("ctrl ")
                    if (alt) append("alt ")
                    if (shift) append("shift ")
                    if (meta) append("meta ")
                    append(base)
                }
            }
        }
}

private fun MacroKeyCombo.toKeyString(keyMap: Map<Long, String>): String =
    buildString {
        if (ctrl) append("ctrl ")
        if (alt) append("alt ")
        if (shift) append("shift ")
        if (meta) append("meta ")
        append(keyMap[keyCode] ?: "UNKNOWN")
    }
