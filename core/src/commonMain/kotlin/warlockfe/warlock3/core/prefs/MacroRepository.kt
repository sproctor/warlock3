package warlockfe.warlock3.core.prefs

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.macro.MacroCommand
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.dao.MacroDao
import warlockfe.warlock3.core.prefs.models.MacroEntity

class MacroRepository(
    val macroDao: MacroDao,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun getGlobalCount(): Int {
        return macroDao.getGlobalCount()
    }

    fun observeGlobalMacros(): Flow<List<MacroCommand>> {
        return macroDao
            .observeGlobals()
            .map { list ->
                list.map { it.toMacroCommand() }
            }
    }

    fun observeCharacterMacros(characterId: String): Flow<List<MacroCommand>> {
        assert(characterId != "global")
        return macroDao
            .observeByCharacterWithGlobals(characterId)
            .map { list ->
                list.map { it.toMacroCommand() }
            }
    }

    fun observeOnlyCharacterMacros(characterId: String): Flow<List<MacroCommand>> {
        assert(characterId != "global")
        return macroDao
            .observeByCharacter(characterId)
            .map { list ->
                list.map { it.toMacroCommand() }
            }
    }

    suspend fun delete(characterId: String, keyCombo: MacroKeyCombo) {
        withContext(NonCancellable) {
            macroDao.delete(
                characterId = characterId,
                keyCode = keyCombo.keyCode,
                ctrl = keyCombo.ctrl,
                alt = keyCombo.alt,
                shift = keyCombo.shift,
                meta = keyCombo.meta,
            )
        }
    }

    suspend fun put(characterId: String, keyCombo: MacroKeyCombo, value: String) {
        withContext(NonCancellable) {
            macroDao.save(
                MacroEntity(
                    characterId = characterId,
                    key = "",
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

    suspend fun migrateMacros(keyMap: Map<String, Long>) {
        val oldMacros = macroDao.getOldMacros()

        oldMacros.forEach { oldMacro ->
            logger.debug { "Migrating macro: $oldMacro" }
            val parts = oldMacro.key.split("+")
            val keyCode = keyMap[parts.last()]
            if (keyCode != null) {
                val entity = MacroEntity(
                    characterId = oldMacro.characterId,
                    key = "",
                    value = oldMacro.value,
                    keyCode = keyCode,
                    ctrl = parts.contains("ctrl"),
                    alt = parts.contains("alt"),
                    shift = parts.contains("shift"),
                    meta = parts.contains("meta"),
                )
                logger.debug { "New macro: $entity" }
                macroDao.save(entity)
                macroDao.deleteByKey(characterId = oldMacro.characterId, key = oldMacro.key)
            } else {
                logger.error { "Could not find keycode for: $oldMacro" }
            }
        }
    }
}

private fun MacroEntity.toMacroCommand(): MacroCommand {
    return MacroCommand(
        keyCombo = MacroKeyCombo(keyCode = keyCode, ctrl = ctrl, alt = alt, shift = shift, meta = meta),
        command = value,
    )
}
