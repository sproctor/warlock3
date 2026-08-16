package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.export.ConnectionExport
import warlockfe.warlock3.core.prefs.export.WarlockExport
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An export carries no credentials, so import rebuilds the account rows from the connections that
 * name them. It must add missing accounts without disturbing the passwords already on this machine.
 */
class ExportImportAccountTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String
    private lateinit var accountDao: FakeAccountDao

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("export-account-test").toAbsolutePath().toString())
        configDir = dir.toString()
        accountDao = FakeAccountDao()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    @Test
    fun import_leavesAnAlreadySavedPasswordAlone() =
        runBlocking {
            accountDao.save(AccountEntity(username = "someuser", password = "hunter2"))

            newRepository().importFull(exportWithConnections("someuser"), resolutions = emptyMap())

            assertEquals("hunter2", accountDao.getByUsername("someuser")?.password)
        }

    @Test
    fun import_registersAccountsNamedByConnections() =
        runBlocking {
            newRepository().importFull(exportWithConnections("newuser"), resolutions = emptyMap())

            val account = accountDao.getByUsername("newuser")
            assertTrue(account != null, "the connection's account should have been registered")
            assertNull(account.password, "a restored account has no password until the user logs in")
        }

    @Test
    fun import_registersEachUsernameOnceAndSkipsBlankOnes() =
        runBlocking {
            newRepository().importFull(
                exportWithConnections("someuser", "someuser", ""),
                resolutions = emptyMap(),
            )

            assertEquals(listOf("someuser"), accountDao.getAll().map { it.username })
        }

    private fun newRepository() =
        ExportRepository(
            accountDao = accountDao,
            characterSettingDao = UnusedCharacterSettingDao,
            clientSettingDao = UnusedClientSettingDao,
            scriptDirDao = UnusedScriptDirDao,
            windowSettingsDao = UnusedWindowSettingsDao,
            characterConfigStore = CharacterConfigStore(configDir, fs),
            clientConfigStore = ClientConfigStore(configDir, fs),
        )

    private fun exportWithConnections(vararg usernames: String) =
        WarlockExport(
            characters = emptyList(),
            connections =
                usernames.mapIndexed { index, username ->
                    ConnectionExport(
                        id = "gs4:char$index",
                        name = "Char $index",
                        username = username,
                        gameCode = "GS4",
                        character = "Char$index",
                        settings = emptyMap(),
                    )
                },
            settings = emptyMap(),
        )
}

private class FakeAccountDao : AccountDao {
    private val accounts = mutableMapOf<String, AccountEntity>()

    override suspend fun getAll(): List<AccountEntity> = accounts.values.toList()

    override fun observeAll(): Flow<List<AccountEntity>> = flowOf(accounts.values.toList())

    override suspend fun getByUsername(username: String): AccountEntity? = accounts[username]

    override suspend fun save(account: AccountEntity) {
        accounts[account.username] = account
    }

    override suspend fun insertIfAbsent(account: AccountEntity) {
        accounts.putIfAbsent(account.username, account)
    }

    override suspend fun delete(username: String) {
        accounts.remove(username)
    }
}

private object UnusedCharacterSettingDao : CharacterSettingDao {
    override suspend fun getByKey(
        key: String,
        characterId: String,
    ): String? = error("unused")

    override fun observeByKey(
        key: String,
        characterId: String,
    ): Flow<String?> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<CharacterSettingEntity> = error("unused")

    override suspend fun save(characterSetting: CharacterSettingEntity) = error("unused")

    override suspend fun delete(
        key: String,
        characterId: String,
    ) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")
}

private object UnusedClientSettingDao : ClientSettingDao {
    override suspend fun getAll(): List<ClientSettingEntity> = error("unused")

    override suspend fun getByKey(key: String): String? = error("unused")

    override fun observeByKey(key: String): Flow<String?> = error("unused")

    override suspend fun removeByKey(key: String) = error("unused")

    override suspend fun save(entity: ClientSettingEntity) = error("unused")
}

private object UnusedScriptDirDao : ScriptDirDao {
    override fun observeByCharacter(characterId: String): Flow<List<String>> = error("unused")

    override suspend fun getByCharacterWithGlobal(characterId: String): List<String> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<String> = error("unused")

    override suspend fun save(scriptDir: ScriptDirEntity) = error("unused")

    override suspend fun delete(
        characterId: String,
        path: String,
    ) = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")
}

private object UnusedWindowSettingsDao : WindowSettingsDao {
    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> = error("unused")

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = error("unused")

    override suspend fun deleteByCharacter(characterId: String) = error("unused")

    override suspend fun save(windowSettings: WindowSettingsEntity) = error("unused")

    override suspend fun openWindow(
        characterId: String,
        name: String,
    ) = error("unused")

    override suspend fun closeWindow(
        characterId: String,
        name: String,
    ) = error("unused")
}
