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
import warlockfe.warlock3.core.prefs.models.Action
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
import kotlin.uuid.Uuid

class ActionsViewModel(
    initialCharacter: GameCharacter?,
    private val actionRepository: ActionRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    /** Every action defined for the selected character, on the button bar or not. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pool: StateFlow<List<Action>> =
        _currentCharacter
            .flatMapLatest { character ->
                actionRepository.observePool(character?.id ?: GLOBAL_CHARACTER_ID)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The ids of the actions on the selected character's button bar, in order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val toolbar: StateFlow<List<Uuid>> =
        _currentCharacter
            .flatMapLatest { character ->
                actionRepository.observeToolbar(character?.id ?: GLOBAL_CHARACTER_ID)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    fun setToolbar(toolbar: List<Uuid>) {
        val characterId = characterId()
        viewModelScope.launch {
            actionRepository.setToolbar(characterId, toolbar)
        }
    }

    fun delete(action: Action) {
        val characterId = characterId()
        viewModelScope.launch {
            actionRepository.deleteAction(characterId, action.id)
        }
    }

    /** Saves [action] into the pool, then calls [onSaved]. */
    fun save(
        action: Action,
        onSaved: () -> Unit,
    ) {
        val characterId = characterId()
        viewModelScope.launch {
            actionRepository.saveAction(characterId, action)
            onSaved()
        }
    }

    private fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID
}
