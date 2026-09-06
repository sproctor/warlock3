package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.persistToDatabase

class AccountRepository(
    private val accountDao: AccountDao,
    private val settingsProblems: SettingsProblems,
) {
    suspend fun getAll(): List<AccountEntity> = accountDao.getAll()

    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()

    suspend fun getByUsername(username: String): AccountEntity? = accountDao.getByUsername(username)

    suspend fun save(account: AccountEntity) {
        persistToDatabase(settingsProblems, "your account") {
            accountDao.save(account)
        }
    }

    suspend fun deleteByUsername(username: String) {
        persistToDatabase(settingsProblems, "your account list") {
            accountDao.delete(username)
        }
    }
}
