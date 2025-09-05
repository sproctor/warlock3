package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.models.AccountEntity

class AccountRepository(
    private val accountDao: AccountDao,
) {
    suspend fun getAll(): List<AccountEntity> {
        return accountDao.getAll()
    }

    suspend fun getByUsername(username: String): AccountEntity? {
        return accountDao.getByUsername(username)
    }

    suspend fun save(account: AccountEntity) {
        withContext(NonCancellable) {
            accountDao.save(account)
        }
    }
}