package warlockfe.warlock3.core.prefs.repositories

import co.touchlab.kermit.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.macro.Macro
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.models.MacroEntity

// TODO: Make Keyboard mappings an interface and pass it here
class MacroRepository(
    val macroDao: MacroDao,
    private val keyMap: Map<String, Long>,
    private val reverseKeyMap: Map<Long, String>,
) {

    private val logger = Logger.withTag("MacroRepository")

    suspend fun getGlobalCount(): Int {
        return macroDao.getGlobalCount()
    }

    fun observeGlobalMacros(): Flow<List<Macro>> {
        return macroDao
            .observeGlobals()
            .map { list ->
                list.map { it.toMacroCommand(keyMap) }
            }
    }

    fun observeCharacterMacros(characterId: String): Flow<List<Macro>> {
        require(characterId != "global")
        return macroDao
            .observeByCharacterWithGlobals(characterId)
            .map { list ->
                list.map { it.toMacroCommand(keyMap) }
            }
    }

    fun observeOnlyCharacterMacros(characterId: String): Flow<List<Macro>> {
        require(characterId != "global")
        return macroDao
            .observeByCharacter(characterId)
            .map { list ->
                list.map { it.toMacroCommand(keyMap) }
            }
    }

    suspend fun delete(characterId: String, keyCombo: MacroKeyCombo) {
        withContext(NonCancellable) {
            macroDao.delete(
                characterId = characterId,
                keyString = keyCombo.toKeyString(reverseKeyMap),
            )
        }
    }

    suspend fun put(characterId: String, keyCombo: MacroKeyCombo, value: String) {
        withContext(NonCancellable) {
            macroDao.save(
                MacroEntity(
                    characterId = characterId,
                    key = keyCombo.toKeyString(reverseKeyMap),
                    value = value,
                    keyCode = keyCombo.keyCode,
                    ctrl = keyCombo.ctrl,
                    alt = keyCombo.alt,
                    shift = keyCombo.shift,
                    meta = keyCombo.meta,
                )
            )
        }
    }

    suspend fun deleteAllGlobals() {
        withContext(NonCancellable) {
            macroDao.deleteAllGlobals()
        }
    }

    suspend fun migrateMacros(keyMap: Map<Long, String>): List<String> {
        val oldMacros = macroDao.getOldMacros()
        val failedMacros = mutableListOf<String>()

        oldMacros.forEach { oldMacro ->
            logger.d { "Migrating macro: $oldMacro" }
            val keyCode = keyMap[oldMacro.keyCode]
            if (keyCode != null) {
                val keyString = buildString {
                    if (oldMacro.ctrl) append("ctrl ")
                    if (oldMacro.alt) append("alt ")
                    if (oldMacro.shift) append("shift ")
                    if (oldMacro.meta) append("meta ")
                    append(keyCode)
                }
                val entity = MacroEntity(
                    characterId = oldMacro.characterId,
                    key = keyString,
                    value = oldMacro.value,
                    keyCode = oldMacro.keyCode,
                    ctrl = oldMacro.ctrl,
                    alt = oldMacro.alt,
                    shift = oldMacro.shift,
                    meta = oldMacro.meta,
                )
                logger.d { "New macro: $entity" }
                macroDao.save(entity)
                macroDao.deleteByKey(characterId = oldMacro.characterId, key = oldMacro.key)
            } else {
                logger.e { "Could not find keycode for: $oldMacro" }
                failedMacros.add(oldMacro.toString())
            }
        }
        return failedMacros
    }
}

private fun MacroEntity.toMacroCommand(
    keyMap: Map<String, Long>
): Macro {
    val parts = key.split(" ")
    return Macro(
        keyCombo = MacroKeyCombo(
            keyCode = keyMap[parts.last()] ?: 0,
            ctrl = parts.contains("ctrl"),
            alt = parts.contains("alt"),
            shift = parts.contains("shift"),
            meta = parts.contains("meta"),
        ),
        action = value,
    )
}

private fun MacroKeyCombo.toKeyString(keyMap: Map<Long, String>): String {
    return buildString {
        if (ctrl) append("ctrl ")
        if (alt) append("alt ")
        if (shift) append("shift ")
        if (meta) append("meta ")
        append(keyMap[keyCode] ?: "UNKNOWN")
    }
}
