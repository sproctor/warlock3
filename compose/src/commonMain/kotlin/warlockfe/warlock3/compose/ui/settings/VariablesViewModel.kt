package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.VariableEntity
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository

class VariablesViewModel(
    initialCharacter: GameCharacter?,
    characterRepository: CharacterRepository,
    private val variableRepository: VariableRepository,
) : ViewModel() {
    private val selectedCharacter = MutableStateFlow(initialCharacter)

    /** Variables have no global scope, so with no character chosen this is the first character. */
    val currentCharacter: StateFlow<GameCharacter?> =
        combine(selectedCharacter, characterRepository.observeAllCharacters()) { selected, characters ->
            selected ?: characters.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, initialCharacter)

    @OptIn(ExperimentalCoroutinesApi::class)
    val variables: StateFlow<List<VariableEntity>> =
        currentCharacter
            .flatMapLatest { character ->
                if (character == null) {
                    flowOf(emptyList())
                } else {
                    variableRepository.observeCharacterVariables(character.id)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun selectCharacter(character: GameCharacter?) {
        selectedCharacter.value = character
    }

    fun delete(variable: VariableEntity) {
        val characterId = currentCharacter.value?.id ?: return
        viewModelScope.launch {
            variableRepository.delete(characterId, variable.name)
        }
    }

    /**
     * Saves [name] = [value], replacing [oldVariable], which may have had another name, then calls
     * [onSaved].
     */
    fun save(
        oldVariable: VariableEntity,
        name: String,
        value: String,
        onSaved: () -> Unit,
    ) {
        val characterId = currentCharacter.value?.id ?: return
        viewModelScope.launch {
            if (name != oldVariable.name) {
                variableRepository.delete(characterId, oldVariable.name)
            }
            variableRepository.put(characterId, name, value)
            onSaved()
        }
    }
}
