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
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository

class AlterationsViewModel(
    initialCharacter: GameCharacter?,
    private val alterationRepository: AlterationRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val alterations: StateFlow<List<AlterationEntity>> =
        _currentCharacter
            .flatMapLatest { character ->
                alterationRepository.observeByCharacter(character?.id ?: GLOBAL_CHARACTER_ID)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    /** The id a new alteration belongs to: the selected character's, or the global one. */
    fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID

    fun delete(alteration: AlterationEntity) {
        viewModelScope.launch {
            alterationRepository.deleteById(alteration.id)
        }
    }

    /** Saves [alteration], then calls [onSaved]. */
    fun save(
        alteration: AlterationEntity,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            alterationRepository.save(alteration)
            onSaved()
        }
    }
}
