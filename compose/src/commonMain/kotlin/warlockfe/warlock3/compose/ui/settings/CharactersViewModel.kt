package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository

/** The character list itself comes from [SettingsViewModel]; this page only deletes from it. */
class CharactersViewModel(
    private val characterRepository: CharacterRepository,
) : ViewModel() {
    /** Deletes [character], then calls [onDeleted], so a confirmation can stay open until it's gone. */
    fun delete(
        character: GameCharacter,
        onDeleted: () -> Unit,
    ) {
        viewModelScope.launch {
            characterRepository.deleteCharacter(character.id)
            onDeleted()
        }
    }
}
