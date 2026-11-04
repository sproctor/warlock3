package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.repositories.AccountRepository

class AccountsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Saves [password] for [account], then calls [onSaved], so a dialog can stay open until it's written. */
    fun setPassword(
        account: AccountEntity,
        password: String,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            accountRepository.save(account.copy(password = password))
            onSaved()
        }
    }

    /** Deletes [account], then calls [onDeleted]. */
    fun delete(
        account: AccountEntity,
        onDeleted: () -> Unit,
    ) {
        viewModelScope.launch {
            accountRepository.deleteByUsername(account.username)
            onDeleted()
        }
    }
}
