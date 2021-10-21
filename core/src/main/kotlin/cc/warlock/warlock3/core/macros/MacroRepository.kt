package cc.warlock.warlock3.core.macros

import kotlinx.coroutines.flow.StateFlow

class MacroRepository(
    val globalMacros: StateFlow<Map<String, String>>,
    val characterMacros: StateFlow<Map<String, Map<String, String>>>,
) {

    fun getMacro(characterId: String, macroKey: String): String? {
        characterMacros.value[characterId.lowercase()]?.get(macroKey)?.let { return it }
        return globalMacros.value[macroKey]
    }
}