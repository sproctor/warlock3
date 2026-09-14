package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.AccountDao
import warlockfe.warlock3.core.prefs.models.AccountEntity
import warlockfe.warlock3.core.sge.ConnectionProtocol
import warlockfe.warlock3.core.sge.TelnetAddress
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A telnet connection is saved with where to dial and identified like a Simutronics character,
 * host standing in for the game code, and survives the trip through connections.toml.
 */
class TelnetConnectionTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("telnet-connection-test").toAbsolutePath().toString())
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private suspend fun repository(): ConnectionRepository {
        val store = ClientConfigStore(dir.toString(), fs)
        store.load()
        return ConnectionRepository(store, NoAccountDao)
    }

    @Test
    fun savedTelnetConnectionRoundTripsThroughTheConfigFile() =
        runBlocking {
            val id =
                repository().saveTelnetConnection(
                    existingId = null,
                    name = "Aardwolf",
                    host = "Aardwolf.org",
                    port = 4000,
                    tls = false,
                    character = "Bob",
                    windowTitle = null,
                )
            assertEquals("aardwolf.org:bob", id)

            // A fresh store reads the file back.
            val connection = repository().getById(id)!!
            assertEquals(ConnectionProtocol.TELNET, connection.protocol)
            assertEquals(TelnetAddress(host = "Aardwolf.org", port = 4000, tls = false), connection.telnetAddress)
            assertEquals("aardwolf.org", connection.code)
            assertEquals("Bob", connection.character)
            assertEquals("Aardwolf", connection.name)
            assertEquals("", connection.username)
            assertNull(connection.password)
            assertFalse(connection.mudMobile)
        }

    @Test
    fun editingKeepsTheIdAndPosition() =
        runBlocking {
            val repository = repository()
            repository.saveTelnetConnection(null, "First", "one.example", 23, false, "A", null)
            val id = repository.saveTelnetConnection(null, "Second", "two.example", 23, false, "B", null)
            repository.saveTelnetConnection(null, "Third", "three.example", 23, false, "C", null)

            val updatedId =
                repository.saveTelnetConnection(
                    existingId = id,
                    name = "Second, moved",
                    host = "elsewhere.example",
                    port = 4443,
                    tls = true,
                    character = "B",
                    windowTitle = "Title",
                )
            assertEquals(id, updatedId)

            val all = repository.observeAllConnections().first()
            assertEquals(listOf("First", "Second, moved", "Third"), all.map { it.name })
            val updated = all[1]
            assertEquals(TelnetAddress("elsewhere.example", 4443, tls = true), updated.telnetAddress)
            // The settings identity is fixed at creation; only the address moved.
            assertEquals("two.example", updated.code)
            assertEquals("Title", updated.windowTitle)
        }

    @Test
    fun aNewConnectionNeverReplacesOneWithTheSameHostAndCharacter() =
        runBlocking {
            val repository = repository()
            val plain = repository.saveTelnetConnection(null, "Plain", "mud.example", 4000, false, "Bob", null)
            val tls = repository.saveTelnetConnection(null, "TLS", "mud.example", 4443, true, "Bob", null)
            val again = repository.saveTelnetConnection(null, "Again", "mud.example", 4000, false, "Bob", null)
            assertEquals("mud.example:bob", plain)
            assertEquals("mud.example:bob-2", tls)
            assertEquals("mud.example:bob-3", again)

            val all = repository.observeAllConnections().first()
            assertEquals(listOf("Plain", "TLS", "Again"), all.map { it.name })
            assertEquals(TelnetAddress("mud.example", 4000, tls = false), all[0].telnetAddress)
            assertEquals(TelnetAddress("mud.example", 4443, tls = true), all[1].telnetAddress)
            // They are the same character on the same MUD, so they share its settings.
            assertEquals(listOf("mud.example"), all.map { it.code }.distinct())
            assertEquals(listOf("Bob"), all.map { it.character }.distinct())
        }

    @Test
    fun decliningTheMudsScriptIsRemembered() =
        runBlocking {
            val repository = repository()
            val id = repository.saveTelnetConnection(null, "Aardwolf", "aardwolf.org", 4000, false, "Bob", null)
            // On by default, as it is in Mudlet.
            assertTrue(repository().getById(id)!!.acceptScripts)

            repository.saveTelnetConnection(id, "Aardwolf", "aardwolf.org", 4000, false, "Bob", null, acceptScripts = false)
            assertFalse(repository().getById(id)!!.acceptScripts)
        }

    @Test
    fun hostIsReducedToACharacterIdSafeGameCode() =
        runBlocking {
            val id = repository().saveTelnetConnection(null, "", "[::1]", 23, false, "Bob", null)
            assertEquals("___1_:bob", id)
        }

    @Test
    fun sgeConnectionsAreUntouched() =
        runBlocking {
            val repository = repository()
            repository.save(username = "user", character = "Alice", gameCode = "DR", name = "Alice_DR")
            val connection = repository.getByName("Alice_DR")!!
            assertEquals(ConnectionProtocol.SGE, connection.protocol)
            assertNull(connection.telnetAddress)
        }
}

private object NoAccountDao : AccountDao {
    override suspend fun getAll(): List<AccountEntity> = emptyList()

    override fun observeAll(): Flow<List<AccountEntity>> = flowOf(emptyList())

    override suspend fun getByUsername(username: String): AccountEntity? = null

    override suspend fun save(account: AccountEntity) {}

    override suspend fun insertIfAbsent(account: AccountEntity) {}

    override suspend fun delete(username: String) {}
}
