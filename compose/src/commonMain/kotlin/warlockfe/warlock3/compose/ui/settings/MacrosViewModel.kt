package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.macro.Macro
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.repositories.MacroRepository

class MacrosViewModel(
    initialCharacter: GameCharacter?,
    private val macroRepository: MacroRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val macros: StateFlow<List<Macro>> =
        _currentCharacter
            .flatMapLatest { character ->
                if (character == null) {
                    macroRepository.observeGlobalMacros()
                } else {
                    macroRepository.observeOnlyCharacterMacros(character.id)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    fun delete(macro: Macro) {
        val characterId = characterId()
        viewModelScope.launch {
            macroRepository.delete(characterId, macro.keyCombo)
        }
    }

    /**
     * Saves [newMacro], replacing [oldMacro] (null when adding), which may have had another key, then
     * calls [onSaved].
     */
    fun save(
        oldMacro: Macro?,
        newMacro: Macro,
        onSaved: () -> Unit,
    ) {
        val characterId = characterId()
        viewModelScope.launch {
            val oldKeyCombo = oldMacro?.keyCombo
            if (oldKeyCombo != null && newMacro.keyCombo != oldKeyCombo) {
                macroRepository.delete(characterId, oldKeyCombo)
            }
            macroRepository.put(characterId, newMacro.keyCombo, newMacro.action)
            onSaved()
        }
    }

    fun resetGlobalMacros() {
        viewModelScope.launch {
            macroRepository.deleteAllGlobals()
            macroRepository.insertDefaultMacrosIfNeeded()
        }
    }

    private fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID
}
