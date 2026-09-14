package warlockfe.warlock3.core.script

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.client.MudScriptOffer
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.script.HttpMudScriptFetcher.Companion.readAtMost
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A MUD's script is fetched once per version and kept with the character, as Mudlet keeps a game's package. */
class MudScriptStoreTest {
    private lateinit var dir: Path
    private lateinit var characterConfigStore: CharacterConfigStore
    private val fetched = mutableListOf<String>()
    private var serves: (String) -> String = { "echo('v1')" }

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("mud-script-store-test").toAbsolutePath().toString())
        characterConfigStore = CharacterConfigStore(dir.toString(), SystemFileSystem)
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun store() =
        MudScriptStore(characterConfigStore, SystemFileSystem) { url ->
            fetched += url
            serves(url)
        }

    private val url = "https://mud.example/warlock.lua"

    @Test
    fun aScriptSentInlineNeedsNoFetchingAndIsNotKept() =
        runBlocking {
            val store = store()
            assertEquals(
                MudScriptLoad.Loaded("echo('inline')"),
                store.load("mud.example:bob", MudScriptOffer("1", script = "echo('inline')")),
            )
            assertTrue(fetched.isEmpty())
            assertNull(store.cached("mud.example:bob"))
        }

    @Test
    fun aScriptIsFetchedOncePerVersion() =
        runBlocking {
            val store = store()
            assertEquals(MudScriptLoad.Loaded("echo('v1')"), store.load("mud.example:bob", MudScriptOffer("1", url = url)))
            assertEquals(listOf(url), fetched)
            assertEquals(MudScriptStore.Cached("1", "echo('v1')"), store.cached("mud.example:bob"))

            // The same version offered again, to a fresh store: the copy on disk serves.
            assertEquals(MudScriptLoad.Loaded("echo('v1')"), store().load("mud.example:bob", MudScriptOffer("1", url = url)))
            assertEquals(listOf(url), fetched)

            // A new version fetches afresh, and replaces the copy.
            serves = { "echo('v2')" }
            assertEquals(MudScriptLoad.Loaded("echo('v2')"), store.load("mud.example:bob", MudScriptOffer("2", url = url)))
            assertEquals(listOf(url, url), fetched)
            assertEquals(MudScriptStore.Cached("2", "echo('v2')"), store.cached("mud.example:bob"))

            // Another character on the same MUD has its own copy.
            assertNull(store.cached("mud.example:alice"))
        }

    @Test
    fun aMudletPackageIsPassedOver() =
        runBlocking {
            val load = store().load("mud.example:bob", MudScriptOffer("39", url = "https://mud.example/ui/Mudlet-UI.mpackage?x=1"))
            assertIs<MudScriptLoad.Skipped>(load)
            assertTrue(fetched.isEmpty())
        }

    @Test
    fun onlyHttpsUrlsAreFetched() =
        runBlocking {
            for (bad in listOf("ftp://mud.example/warlock.lua", "http://mud.example/warlock.lua", "not a url")) {
                val load = store().load("mud.example:bob", MudScriptOffer("1", url = bad))
                assertIs<MudScriptLoad.Failed>(load, bad)
            }
            assertTrue(fetched.isEmpty())
        }

    @Test
    fun nothingIsFetchedFromThisMachineOrItsNetwork() =
        runBlocking {
            val bad =
                listOf(
                    "https://localhost/warlock.lua",
                    "https://LOCALHOST:8443/warlock.lua",
                    "https://router.localhost/warlock.lua",
                    "https://127.0.0.1/warlock.lua",
                    "https://127.1.2.3/x.lua",
                    "https://10.0.0.5/x.lua",
                    "https://172.16.0.1/x.lua",
                    "https://172.31.255.255/x.lua",
                    "https://192.168.1.1/x.lua",
                    "https://169.254.169.254/latest/meta-data",
                    "https://100.64.0.1/x.lua",
                    "https://0.0.0.0/x.lua",
                    "https://224.0.0.1/x.lua",
                    "https://[::1]/x.lua",
                    "https://[::]/x.lua",
                    "https://[fd12::1]/x.lua",
                    "https://[fe80::1]/x.lua",
                    "https://[::ffff:127.0.0.1]/x.lua",
                    "https://2130706433/x.lua",
                    "https://0x7f000001/x.lua",
                )
            for (url in bad) {
                val load = store().load("mud.example:bob", MudScriptOffer("1", url = url))
                assertIs<MudScriptLoad.Failed>(load, url)
            }
            assertTrue(fetched.isEmpty())
            // Public addresses and names are fine.
            for (host in listOf("mud.example", "203.0.113.7", "172.32.0.1", "8.8.8.8", "[2001:db8::1]")) {
                val load = store().load("mud.example:bob", MudScriptOffer("1", url = "https://$host/x.lua"))
                assertIs<MudScriptLoad.Loaded>(load, host)
            }
        }

    @Test
    fun theCopyOnDiskIsOneFileWithItsVersionOnTheFirstLine() =
        runBlocking {
            store().load("mud.example:bob", MudScriptOffer("3", url = url))
            val dir = characterConfigStore.directoryFor("mud.example:bob")
            val file =
                java.nio.file.Path
                    .of(dir.toString(), MudScriptStore.SCRIPT_FILE)
            assertEquals(MudScriptStore.VERSION_HEADER + "3\necho('v1')", Files.readString(file))
            assertEquals(
                listOf(MudScriptStore.SCRIPT_FILE),
                Files
                    .list(
                        java.nio.file.Path
                            .of(dir.toString()),
                    ).map { it.fileName.toString() }
                    .toList(),
            )

            // A file without the version line, from some other hand, is not trusted as a cache.
            Files.writeString(file, "echo('who knows')")
            assertNull(store().cached("mud.example:bob"))
        }

    @Test
    fun theResponseIsCutOffOnceItIsTooBig() =
        runBlocking<Unit> {
            val small = ByteArray(HttpMudScriptFetcher.MAX_BYTES) { 'a'.code.toByte() }
            assertEquals(small.size, ByteReadChannel(small).readAtMost(HttpMudScriptFetcher.MAX_BYTES).size)
            val big = ByteArray(HttpMudScriptFetcher.MAX_BYTES + 1) { 'a'.code.toByte() }
            assertFailsWith<IOException> { ByteReadChannel(big).readAtMost(HttpMudScriptFetcher.MAX_BYTES) }
        }

    @Test
    fun aFailedFetchIsReported() =
        runBlocking {
            serves = { throw IOException("HTTP 404 Not Found") }
            val load = store().load("mud.example:bob", MudScriptOffer("1", url = url))
            assertIs<MudScriptLoad.Failed>(load)
            assertTrue(load.message.contains("HTTP 404 Not Found"), load.message)
            assertNull(store().cached("mud.example:bob"))
        }
}
