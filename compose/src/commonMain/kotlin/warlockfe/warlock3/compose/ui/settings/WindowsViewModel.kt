package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.models.WindowSettings
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer

/** One character's saved window settings. */
data class CharacterWindowSettings(
    val characterId: String,
    val settings: List<WindowSettings>,
)

/**
 * Per-window settings for one character at a time. Windows have no global settings, so with no character
 * picked the page edits the first one. Showing and hiding a window while that character is connected
 * goes through the live game instead (see [WindowSettingsLiveContext]); this only writes the repository.
 */
class WindowsViewModel(
    initialCharacter: GameCharacter?,
    characterRepository: CharacterRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val windowSettingRepository: WindowSettingsRepository,
) : ViewModel() {
    private val selectedCharacter = MutableStateFlow(initialCharacter)

    val currentCharacter: StateFlow<GameCharacter?> =
        combine(selectedCharacter, characterRepository.observeAllCharacters()) { selected, characters ->
            selected ?: characters.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialCharacter)

    /** The character's own base text style; the view resolves it over [globalBaseStyle] and the skin. */
    val characterBaseStyle: StateFlow<StyleLayer> =
        observeCharacter(StyleLayer()) { characterSettingsRepository.observeBaseStyle(it) }

    val globalBaseStyle: StateFlow<StyleLayer> =
        characterSettingsRepository
            .observeBaseStyle(GLOBAL_CHARACTER_ID)
            .stateIn(viewModelScope, SharingStarted.Eagerly, StyleLayer())

    val characterPanelScale: StateFlow<Float?> =
        observeCharacter(null) { characterSettingsRepository.observePanelScale(it) }

    /**
     * The current character's saved window settings, or null until they first load. Tagged with the
     * character they belong to, since after a switch the previous character's stay here until the new
     * ones arrive.
     */
    val windowSettings: StateFlow<CharacterWindowSettings?> =
        observeCharacter(null) { characterId ->
            windowSettingRepository.observeWindowSettings(characterId).map { CharacterWindowSettings(characterId, it) }
        }

    fun selectCharacter(character: GameCharacter?) {
        selectedCharacter.value = character
    }

    /** Shows or hides [name] in the stored layout, applied on the character's next connect. */
    fun setWindowShown(
        name: String,
        show: Boolean,
    ) {
        val characterId = characterId() ?: return
        viewModelScope.launch {
            if (show) {
                windowSettingRepository.openWindow(characterId, name)
            } else {
                windowSettingRepository.closeWindow(characterId, name)
            }
        }
    }

    fun saveStyle(
        name: String,
        layer: StyleLayer,
    ) {
        val characterId = characterId() ?: return
        viewModelScope.launch { windowSettingRepository.setStyleLayer(characterId, name, layer) }
    }

    fun saveFont(
        name: String,
        font: FontConfig?,
    ) {
        val characterId = characterId() ?: return
        viewModelScope.launch { windowSettingRepository.setFont(characterId, name, font) }
    }

    fun saveMonoFont(
        name: String,
        font: FontConfig?,
    ) {
        val characterId = characterId() ?: return
        viewModelScope.launch { windowSettingRepository.setMonoFont(characterId, name, font) }
    }

    fun saveNameFilter(
        name: String,
        value: Boolean,
    ) {
        val characterId = characterId() ?: return
        viewModelScope.launch { windowSettingRepository.setNameFilter(characterId, name, value) }
    }

    fun saveScale(
        name: String,
        value: Float?,
    ) {
        val characterId = characterId() ?: return
        viewModelScope.launch { windowSettingRepository.setScale(characterId, name, value) }
    }

    fun revert(name: String) {
        val characterId = characterId() ?: return
        viewModelScope.launch {
            windowSettingRepository.setStyle(characterId, name, StyleDefinition())
            windowSettingRepository.setFont(characterId, name, null)
            windowSettingRepository.setMonoFont(characterId, name, null)
            windowSettingRepository.setScale(characterId, name, null)
        }
    }

    private fun characterId(): String? = currentCharacter.value?.id

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observeCharacter(
        initial: T,
        observe: (characterId: String) -> Flow<T>,
    ): StateFlow<T> =
        currentCharacter
            .flatMapLatest { character ->
                if (character == null) flowOf(initial) else observe(character.id)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, initial)
}
