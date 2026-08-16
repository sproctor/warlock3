package warlockfe.warlock3.core.prefs.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.models.AccountEntity

@Dao
interface AccountDao {
    @Query("SELECT * FROM account")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM account")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE username = :username")
    suspend fun getByUsername(username: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(account: AccountEntity)

    /**
     * Register an account only if that username isn't already known. Unlike [save] this never
     * touches an existing row, so a caller that only has a username -- restoring a backup, which
     * deliberately carries no credentials -- can add the account without clearing a stored password.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(account: AccountEntity)

    @Query("DELETE FROM account WHERE username = :username")
    suspend fun delete(username: String)
}
