package warlockfe.warlock3.core.script

import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import warlockfe.warlock3.core.client.MudScriptOffer
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun onlyHttpUrlsAreFetched() =
        runBlocking {
            val load = store().load("mud.example:bob", MudScriptOffer("1", url = "ftp://mud.example/warlock.lua"))
            assertIs<MudScriptLoad.Failed>(load)
            assertTrue(fetched.isEmpty())
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
