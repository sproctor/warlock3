package warlockfe.warlock3.core.prefs

import warlockfe.warlock3.core.prefs.models.Account
import warlockfe.warlock3.core.prefs.sql.AccountQueries
import warlockfe.warlock3.core.prefs.sql.Account as DatabaseAccount
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class AccountRepository(
    private val accountQueries: AccountQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getAll(): List<Account> {
        return withContext(ioDispatcher) {
            accountQueries.getAll()
                .executeAsList()
                .map { Account(it.username, it.password ?: "") }
        }
    }

    suspend fun getByUsername(username: String): Account? {
        return withContext(ioDispatcher) {
            accountQueries.getByUsername(username)
                .executeAsOneOrNull()
                ?.let { Account(it.username, it.password ?: "") }
        }
    }

    suspend fun save(account: Account) {
        withContext(ioDispatcher) {
            accountQueries.save(DatabaseAccount(account.username, account.password))
        }
    }
}