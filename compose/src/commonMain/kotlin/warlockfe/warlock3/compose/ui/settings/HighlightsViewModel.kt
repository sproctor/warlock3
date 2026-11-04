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
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.text.StyleLayer

class HighlightsViewModel(
    initialCharacter: GameCharacter?,
    private val highlightRepository: HighlightRepositoryImpl,
    characterSettingsRepository: CharacterSettingsRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val highlights: StateFlow<List<Highlight>> =
        _currentCharacter
            .flatMapLatest { character ->
                if (character == null) {
                    highlightRepository.observeGlobal()
                } else {
                    highlightRepository.observeByCharacter(character.id)
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

    fun move(
        from: Int,
        to: Int,
    ) {
        val characterId = characterId()
        viewModelScope.launch {
            highlightRepository.move(characterId, from, to)
        }
    }

    fun delete(highlight: Highlight) {
        viewModelScope.launch {
            highlightRepository.deleteById(highlight.id)
        }
    }

    /** Saves [highlight] to the selected scope, then calls [onSaved]. */
    fun save(
        highlight: Highlight,
        onSaved: () -> Unit,
    ) {
        val character = _currentCharacter.value
        viewModelScope.launch {
            if (character != null) {
                highlightRepository.save(character.id, highlight)
            } else {
                highlightRepository.saveGlobal(highlight)
            }
            onSaved()
        }
    }

    private fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID
}
