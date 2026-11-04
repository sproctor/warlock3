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
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.text.StyleLayer

class NamesViewModel(
    initialCharacter: GameCharacter?,
    private val nameRepository: NameRepositoryImpl,
    characterSettingsRepository: CharacterSettingsRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val names: StateFlow<List<NameConfig>> =
        _currentCharacter
            .flatMapLatest { character ->
                if (character == null) {
                    nameRepository.observeGlobal()
                } else {
                    nameRepository.observeByCharacter(character.id)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The selected scope's base style (the global one when no character is selected). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val characterBaseStyle: StateFlow<StyleLayer> =
        _currentCharacter
            .flatMapLatest { character ->
                characterSettingsRepository.observeBaseStyle(character?.id ?: GLOBAL_CHARACTER_ID)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, StyleLayer())

    val globalBaseStyle: StateFlow<StyleLayer> =
        characterSettingsRepository
            .observeBaseStyle(GLOBAL_CHARACTER_ID)
            .stateIn(viewModelScope, SharingStarted.Eagerly, StyleLayer())

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    fun delete(name: NameConfig) {
        val characterId = characterId()
        viewModelScope.launch {
            nameRepository.deleteByText(characterId, name.text)
        }
    }

    /** Saves [name] to the selected scope, then calls [onSaved]. */
    fun save(
        name: NameConfig,
        onSaved: () -> Unit,
    ) {
        val characterId = characterId()
        viewModelScope.launch {
            nameRepository.save(characterId, name)
            onSaved()
        }
    }

    private fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID
}
