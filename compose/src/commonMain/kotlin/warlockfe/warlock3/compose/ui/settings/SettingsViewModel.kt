package warlockfe.warlock3.compose.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository

/** State shared by every settings page: the characters a page can pick between. */
class SettingsViewModel(
    characterRepository: CharacterRepository,
) : ViewModel() {
    val characters: StateFlow<List<GameCharacter>> =
        characterRepository
            .observeAllCharacters()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

/**
 * Gives [content] a view-model store of its own, cleared when it leaves the composition or a key
 * changes. Settings is a dialog on desktop, with no navigation entry to clear its view models, so
 * without this they would land in the app window's store and outlive it: reopened, a page would
 * show the character it was last left on.
 */
@Composable
fun ProvideSettingsViewModelStore(
    key1: Any? = null,
    key2: Any? = null,
    content: @Composable () -> Unit,
) {
    key(key1, key2) {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides rememberViewModelStoreOwner(),
            content = content,
        )
    }
}
