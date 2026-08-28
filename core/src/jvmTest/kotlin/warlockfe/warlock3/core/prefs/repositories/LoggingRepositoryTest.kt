package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.util.WarlockDirs
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The settings a [LoggingRepository] logs under are shared into a StateFlow on a scope of the
 * caller's choosing, and a game line can arrive before that sharing coroutine has had a chance to
 * run. These cover the two ways that used to leave the repository with no settings at all, each of
 * which turned the next logged line into a crash ("Attempted to print log before settings were
 * loaded") that took the client down mid-session.
 */
class LoggingRepositoryTest {
    private lateinit var dir: Path
    private lateinit var configDir: String
    private lateinit var logDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("logging-test").toAbsolutePath().toString())
        configDir = Path(dir, "config").toString()
        logDir = Path(dir, "logs").toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    /**
     * The sharing coroutine is dispatched rather than started in place, so on a busy dispatcher it
     * can sit queued behind other work while the game is already sending lines. [NeverDispatched] is
     * that delay taken to its limit: the coroutine never runs, and logging still has to work.
     */
    @Test
    fun logsBeforeTheSharingCoroutineHasRun() {
        val scope = CoroutineScope(SupervisorJob() + NeverDispatched)
        try {
            val repository = LoggingRepository(newClientSettings(), scope)
            runBlocking { repository.logSimple("gs4_tholan") { "You are stunned." } }
            assertLogged("gs4_tholan", "You are stunned.")
        } finally {
            scope.cancel()
        }
    }

    /**
     * Under an unconfined dispatcher the sharing coroutine starts inside the constructor, before the
     * rest of the object's properties are assigned. Its first act is to clear the map of open
     * loggers, so that map has to already exist; when it didn't, the coroutine died of a NPE and the
     * settings were never published at all.
     */
    @Test
    fun startingTheSharingCoroutineDuringConstructionDoesNotFail() {
        val failures = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, throwable -> failures.add(throwable) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
        try {
            val repository = LoggingRepository(newClientSettings(), scope)
            assertTrue(failures.isEmpty(), "sharing coroutine failed: ${failures.firstOrNull()}")
            runBlocking { repository.logSimple("gs4_tholan") { "You are stunned." } }
            assertLogged("gs4_tholan", "You are stunned.")
        } finally {
            scope.cancel()
        }
    }

    private fun newClientSettings(): ClientSettingRepository =
        ClientSettingRepository(
            clientSettingDao = LoggingTestClientSettingDao,
            clientConfigStore = ClientConfigStore(configDir, SystemFileSystem),
            warlockDirs =
                WarlockDirs(
                    homeDir = dir.toString(),
                    dataDir = dir.toString(),
                    configDir = configDir,
                    logDir = logDir,
                ),
        )

    /** Log lines carry a leading timestamp by default, so only the tail of the line is compared. */
    private fun assertLogged(
        name: String,
        message: String,
    ) {
        val line = awaitLoggedLine(name)
        assertTrue(line != null && line.endsWith(message), "expected a line ending in \"$message\", got $line")
    }

    /** [FileLogger] writes on a thread of its own, so the line lands a moment after logging it. */
    private fun awaitLoggedLine(name: String): String? {
        val directory = File(logDir, name)
        val deadline = TimeSource.Monotonic.markNow() + 10.seconds
        while (true) {
            val line =
                directory
                    .listFiles()
                    ?.firstOrNull()
                    ?.readText()
                    ?.trim()
            if (!line.isNullOrEmpty()) return line
            if (deadline.hasPassedNow()) return null
            Thread.sleep(20)
        }
    }
}

/** Accepts work and never runs it, standing in for a dispatcher that hasn't gotten to it yet. */
private object NeverDispatched : CoroutineDispatcher() {
    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) = Unit
}

private object LoggingTestClientSettingDao : ClientSettingDao {
    override suspend fun getAll(): List<ClientSettingEntity> = error("unused")

    override suspend fun getByKey(key: String): String? = error("unused")

    override fun observeByKey(key: String): Flow<String?> = flowOf(null)

    override suspend fun removeByKey(key: String) = error("unused")

    override suspend fun save(entity: ClientSettingEntity) = error("unused")
}
