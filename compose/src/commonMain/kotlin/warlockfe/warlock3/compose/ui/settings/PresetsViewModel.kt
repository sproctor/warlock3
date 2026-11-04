package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleLayer

/**
 * The text styles page: the base style, named presets, fonts and panel scale of the selected scope
 * (a character, or the global scope when none is selected), plus the global layers beneath them.
 */
class PresetsViewModel(
    initialCharacter: GameCharacter?,
    private val presetRepository: PresetRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    val characterPresets: StateFlow<Map<String, StyleLayer>> =
        scoped { presetRepository.observeScopeLayers(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val globalPresets: StateFlow<Map<String, StyleLayer>> =
        presetRepository
            .observeScopeLayers(GLOBAL_CHARACTER_ID)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val characterBaseStyle: StateFlow<StyleLayer> =
        scoped { characterSettingsRepository.observeBaseStyle(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, StyleLayer())

    val globalBaseStyle: StateFlow<StyleLayer> =
        characterSettingsRepository
            .observeBaseStyle(GLOBAL_CHARACTER_ID)
            .stateIn(viewModelScope, SharingStarted.Eagerly, StyleLayer())

    val monoFont: StateFlow<FontConfig?> =
        scoped { characterSettingsRepository.observeMonoFont(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val panelFont: StateFlow<FontConfig?> =
        scoped { characterSettingsRepository.observePanelFont(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val panelScale: StateFlow<Float?> =
        scoped { characterSettingsRepository.observePanelScale(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    fun saveBaseStyle(layer: StyleLayer) {
        val scopeId = scopeId()
        viewModelScope.launch {
            characterSettingsRepository.saveBaseStyle(scopeId, layer)
        }
    }

    fun savePresetLayer(
        name: String,
        layer: StyleLayer,
    ) {
        val scopeId = scopeId()
        viewModelScope.launch {
            presetRepository.saveLayer(scopeId, name, layer)
        }
    }

    fun saveMonoFont(font: FontConfig?) {
        val scopeId = scopeId()
        viewModelScope.launch {
            characterSettingsRepository.saveMonoFont(scopeId, font)
        }
    }

    fun savePanelFont(font: FontConfig?) {
        val scopeId = scopeId()
        viewModelScope.launch {
            characterSettingsRepository.savePanelFont(scopeId, font)
        }
    }

    fun savePanelScale(scale: Float?) {
        val scopeId = scopeId()
        viewModelScope.launch {
            characterSettingsRepository.savePanelScale(scopeId, scale)
        }
    }

    /** [observe] for the selected scope, following the selection. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> scoped(observe: (String) -> Flow<T>): Flow<T> =
        _currentCharacter.flatMapLatest { character -> observe(character?.id ?: GLOBAL_CHARACTER_ID) }

    private fun scopeId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID
}
