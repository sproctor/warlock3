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
import warlockfe.warlock3.core.prefs.models.Ignore
import warlockfe.warlock3.core.prefs.repositories.IgnoreRepository

class IgnoresViewModel(
    initialCharacter: GameCharacter?,
    private val ignoreRepository: IgnoreRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val ignores: StateFlow<List<Ignore>> =
        _currentCharacter
            .flatMapLatest { character ->
                ignoreRepository.observeByCharacter(character?.id ?: GLOBAL_CHARACTER_ID)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    fun delete(ignore: Ignore) {
        viewModelScope.launch {
            ignoreRepository.deleteById(ignore.id)
        }
    }

    /** Saves [ignore] to the selected scope, then calls [onSaved]. */
    fun save(
        ignore: Ignore,
        onSaved: () -> Unit,
    ) {
        val characterId = characterId()
        viewModelScope.launch {
            ignoreRepository.save(characterId, ignore)
            onSaved()
        }
    }

    private fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID
}
