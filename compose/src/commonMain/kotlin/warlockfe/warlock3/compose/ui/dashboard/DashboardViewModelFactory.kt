package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.ui.platform.ClipboardManager
import kotlinx.coroutines.CoroutineDispatcher
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.game.GameViewModelFactory
import warlockfe.warlock3.core.client.WarlockClientFactory
import warlockfe.warlock3.core.prefs.AccountRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.sge.SgeClientFactory

class DashboardViewModelFactory(
    private val characterRepository: CharacterRepository,
    private val accountRepository: AccountRepository,
    private val gameViewModelFactory: GameViewModelFactory,
    private val sgeClientFactory: SgeClientFactory,
    private val warlockClientFactory: WarlockClientFactory,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun create(
        updateGameState: (GameState) -> Unit,
        clipboardManager: ClipboardManager,
    ): DashboardViewModel {
        return DashboardViewModel(
            characterRepository = characterRepository,
            accountRepository = accountRepository,
            updateGameState = updateGameState,
            clipboardManager = clipboardManager,
            gameViewModelFactory = gameViewModelFactory,
            sgeClientFactory = sgeClientFactory,
            warlockClientFactory = warlockClientFactory,
            ioDispatcher = ioDispatcher,
        )
    }
}
