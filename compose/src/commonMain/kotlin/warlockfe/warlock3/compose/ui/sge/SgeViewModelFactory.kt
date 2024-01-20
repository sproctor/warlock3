package warlockfe.warlock3.compose.ui.sge

import androidx.compose.ui.platform.ClipboardManager
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.sge.SgeClientFactory

class SgeViewModelFactory(
    private val clientSettingRepository: ClientSettingRepository,
    private val accountRepository: AccountRepository,
    private val characterRepository: CharacterRepository,
    private val warlockClientFactory: WarlockClientFactory,
    private val sgeClientFactory: SgeClientFactory,
    private val gameViewModelFactory: GameViewModelFactory,
) {
    fun create(
        clipboardManager: ClipboardManager,
        updateGameState: (GameState) -> Unit,
    ): SgeViewModel {
        return SgeViewModel(
            clientSettingRepository = clientSettingRepository,
            accountRepository = accountRepository,
            clipboardManager = clipboardManager,
            characterRepository = characterRepository,
            warlockClientFactory = warlockClientFactory,
            sgeClientFactory = sgeClientFactory,
            gameViewModelFactory = gameViewModelFactory,
            updateGameState = updateGameState,
        )
    }
}