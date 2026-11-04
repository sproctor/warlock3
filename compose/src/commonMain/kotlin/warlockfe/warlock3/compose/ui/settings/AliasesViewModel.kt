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
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.prefs.repositories.AliasRepository

class AliasesViewModel(
    initialCharacter: GameCharacter?,
    private val aliasRepository: AliasRepository,
) : ViewModel() {
    private val _currentCharacter = MutableStateFlow(initialCharacter)
    val currentCharacter: StateFlow<GameCharacter?> = _currentCharacter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val aliases: StateFlow<List<AliasEntity>> =
        _currentCharacter
            .flatMapLatest { character ->
                aliasRepository.observeByCharacter(character?.id ?: GLOBAL_CHARACTER_ID)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCharacter(character: GameCharacter?) {
        _currentCharacter.value = character
    }

    /** The id a new alias belongs to: the selected character's, or the global one. */
    fun characterId(): String = _currentCharacter.value?.id ?: GLOBAL_CHARACTER_ID

    fun delete(alias: AliasEntity) {
        viewModelScope.launch {
            aliasRepository.deleteById(alias.id)
        }
    }

    /** Saves [alias], then calls [onSaved]. */
    fun save(
        alias: AliasEntity,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            aliasRepository.save(alias)
            onSaved()
        }
    }
}
