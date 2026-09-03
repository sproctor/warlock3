package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.SettingsProblems
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.dao.CharacterSettingDao
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.dao.ScriptDirDao
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.prefs.models.CharacterSettingEntity
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.prefs.models.ScriptDirEntity
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.prefs.persistToDatabase
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A settings write that goes to SQLite must not throw, and must not fail silently either: it is
 * launched from `viewModelScope` and never awaited, so there is no caller to catch it and nothing on
 * screen already waiting to report it. Letting it escape sends it to the thread's uncaught handler,
 * which is how a disk that would not `fsync` turned a settings write into a fatal-level crash report
 * that the person losing their settings never saw (DESKTOP-3X, `SQLiteException: Error code: 1034,
 * message: disk I/O error`). It goes to [SettingsProblems], and from there to the user, instead.
 *
 * The failing DAOs below stand in for that disk: every write throws, every read works, which is what
 * a database on a full or failing volume looks like.
 */
class DatabaseWriteFailureTest {
    private val character = "dr:tholan"
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("db-write-failure-test").toAbsolutePath().toString())
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun warlockDirs(): WarlockDirs =
        WarlockDirs(
            homeDir = dir.toString(),
            dataDir = dir.toString(),
            configDir = dir.toString(),
            logDir = dir.toString(),
        )

    @Test
    fun openingAndClosingWindowsSurvivesAnUnwritableDatabase() =
        runBlocking {
            val store = CharacterConfigStore(dir.toString(), SystemFileSystem).also { it.load() }
            val problems = SettingsProblems(dir.toString())
            val repository = WindowSettingsRepository(FailingWindowSettingsDao, store, problems)

            repository.openWindow(character, "combat")
            repository.closeWindow(character, "combat")
            repository.removeWindowFromLayout(character, "combat")

            assertEquals("Warlock could not save your window layout.", problems.current.value?.headline)

            // The config half of a close is written by its own store, so it still lands: losing the
            // open flag to a bad disk should not also lose the flag that keeps the game from
            // reopening a window the user just closed.
            assertTrue(repository.isHidden(character, "combat"))
        }

    @Test
    fun savingAClientSettingSurvivesAnUnwritableDatabase() =
        runBlocking {
            val problems = SettingsProblems(dir.toString())
            val repository =
                ClientSettingRepository(
                    clientSettingDao = FailingClientSettingDao,
                    clientConfigStore = ClientConfigStore(dir.toString(), SystemFileSystem).also { it.load() },
                    warlockDirs = warlockDirs(),
                    settingsProblems = problems,
                )

            repository.putWidth(1280)
            repository.putLastUsername("someuser")

            assertEquals("Warlock could not save your application settings.", problems.current.value?.headline)
        }

    @Test
    fun savingACharacterSettingSurvivesAnUnwritableDatabase() =
        runBlocking {
            val store = CharacterConfigStore(dir.toString(), SystemFileSystem).also { it.load() }
            val problems = SettingsProblems(dir.toString())
            val repository = CharacterSettingsRepository(FailingCharacterSettingDao, store, problems)

            repository.save(character, "mainWindowWidth", "1280")

            assertEquals("Warlock could not save this character's settings.", problems.current.value?.headline)
        }

    @Test
    fun savingAnAccountSurvivesAnUnwritableDatabase() =
        runBlocking {
            val problems = SettingsProblems(dir.toString())
            val repository = AccountRepository(FailingAccountDao, problems)

            repository.save(AccountEntity(username = "someuser", password = "hunter2"))
            repository.deleteByUsername("someuser")

            assertEquals("Warlock could not save your account.", problems.current.value?.headline)
        }

    @Test
    fun savingAScriptDirectorySurvivesAnUnwritableDatabase() =
        runBlocking {
            val problems = SettingsProblems(dir.toString())
            val repository =
                ScriptDirRepository(
                    scriptDirDao = FailingScriptDirDao,
                    warlockDirs = warlockDirs(),
                    settingsProblems = problems,
                )

            repository.save(character, "/scripts")
            repository.delete(character, "/scripts")

            assertEquals("Warlock could not save your script folders.", problems.current.value?.headline)
        }

    /**
     * A disk that has stopped taking writes fails every save there is -- every resize, every window
     * toggle -- so the user hears about it once and then not again until saving works, or dismissing
     * the report would only summon the next one.
     */
    @Test
    fun theUserIsToldOnceNoMatterHowManyWritesFail() =
        runBlocking {
            val dao = SwitchableClientSettingDao(failing = true)
            val problems = SettingsProblems(dir.toString())
            val repository = clientSettings(dao, problems)

            repository.putWidth(1280)
            val first = problems.current.value
            repository.putHeight(720)
            repository.putLastUsername("someuser")

            assertEquals(first, problems.current.value)

            problems.dismiss()
            assertNull(problems.current.value)

            repository.putWidth(1281)
            assertNull(problems.current.value)
        }

    @Test
    fun aProblemThatComesBackIsReportedAgain() =
        runBlocking {
            val dao = SwitchableClientSettingDao(failing = true)
            val problems = SettingsProblems(dir.toString())
            val repository = clientSettings(dao, problems)

            repository.putWidth(1280)
            problems.dismiss()

            dao.failing = false
            repository.putWidth(1281)
            dao.failing = true
            repository.putWidth(1282)

            assertEquals("Warlock could not save your application settings.", problems.current.value?.headline)
        }

    /** What the user reads has to say what broke and where their settings live. */
    @Test
    fun theReportNamesTheFailureAndWhereSettingsAreKept() =
        runBlocking {
            val problems = SettingsProblems(dir.toString())

            clientSettings(SwitchableClientSettingDao(failing = true), problems).putWidth(1280)

            val message = problems.current.value?.message ?: fail("no failure was reported")
            assertContains(message, "your application settings")
            assertContains(message, "Error code: 1034")
            assertContains(message, dir.toString())
        }

    private fun clientSettings(
        dao: ClientSettingDao,
        problems: SettingsProblems,
    ) = ClientSettingRepository(
        clientSettingDao = dao,
        clientConfigStore = ClientConfigStore(dir.toString(), SystemFileSystem),
        warlockDirs = warlockDirs(),
        settingsProblems = problems,
    )

    /**
     * Swallowing the failure must not cost the other half of what these writes are for: a setting
     * saved as its screen closes is written from a scope that is already being cancelled, and it
     * still has to reach the disk.
     */
    @Test
    fun aWriteAlreadyRunningFinishesEvenWhenItsCallerIsCancelled() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var finished = false

            val job =
                launch {
                    persistToDatabase(SettingsProblems(dir.toString()), "your settings") {
                        started.complete(Unit)
                        release.await()
                        finished = true
                    }
                }

            started.await()
            job.cancel()
            release.complete(Unit)
            job.join()

            assertTrue(finished)
        }
}

private fun unwritable(): Nothing = throw IOException("Error code: 1034, message: disk I/O error")

private object FailingWindowSettingsDao : WindowSettingsDao {
    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> = emptyList()

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = null

    override suspend fun save(windowSettings: WindowSettingsEntity) = unwritable()

    override suspend fun openWindow(
        characterId: String,
        name: String,
    ) = unwritable()

    override suspend fun closeWindow(
        characterId: String,
        name: String,
    ) = unwritable()

    override suspend fun deleteByCharacter(characterId: String) = unwritable()
}

/** A database that can be broken and mended, for the "told once, told again" paths. */
private class SwitchableClientSettingDao(
    var failing: Boolean,
) : ClientSettingDao {
    override suspend fun getAll(): List<ClientSettingEntity> = emptyList()

    override suspend fun getByKey(key: String): String? = null

    override fun observeByKey(key: String): Flow<String?> = error("unused")

    override suspend fun removeByKey(key: String) = save(ClientSettingEntity(key, null))

    override suspend fun save(entity: ClientSettingEntity) {
        if (failing) unwritable()
    }
}

private object FailingClientSettingDao : ClientSettingDao {
    override suspend fun getAll(): List<ClientSettingEntity> = emptyList()

    override suspend fun getByKey(key: String): String? = null

    override fun observeByKey(key: String): Flow<String?> = error("unused")

    override suspend fun removeByKey(key: String) = unwritable()

    override suspend fun save(entity: ClientSettingEntity) = unwritable()
}

private object FailingCharacterSettingDao : CharacterSettingDao {
    override suspend fun getByKey(
        key: String,
        characterId: String,
    ): String? = null

    override fun observeByKey(
        key: String,
        characterId: String,
    ): Flow<String?> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<CharacterSettingEntity> = emptyList()

    override suspend fun save(characterSetting: CharacterSettingEntity) = unwritable()

    override suspend fun delete(
        key: String,
        characterId: String,
    ) = unwritable()

    override suspend fun deleteByCharacter(characterId: String) = unwritable()
}

private object FailingAccountDao : AccountDao {
    override suspend fun getAll(): List<AccountEntity> = emptyList()

    override fun observeAll(): Flow<List<AccountEntity>> = error("unused")

    override suspend fun getByUsername(username: String): AccountEntity? = null

    override suspend fun save(account: AccountEntity) = unwritable()

    override suspend fun insertIfAbsent(account: AccountEntity) = unwritable()

    override suspend fun delete(username: String) = unwritable()
}

private object FailingScriptDirDao : ScriptDirDao {
    override fun observeByCharacter(characterId: String): Flow<List<String>> = error("unused")

    override suspend fun getByCharacterWithGlobal(characterId: String): List<String> = emptyList()

    override suspend fun getByCharacter(characterId: String): List<String> = emptyList()

    override suspend fun save(scriptDir: ScriptDirEntity) = unwritable()

    override suspend fun delete(
        characterId: String,
        path: String,
    ) = unwritable()

    override suspend fun deleteByCharacter(characterId: String) = unwritable()
}
