package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore
import warlockfe.warlock3.core.prefs.models.Action
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ActionRepositoryTest {
    private val fs = SystemFileSystem
    private lateinit var dir: Path
    private lateinit var configDir: String

    @BeforeTest
    fun setUp() {
        dir = Path(Files.createTempDirectory("action-test").toAbsolutePath().toString())
        configDir = dir.toString()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        java.nio.file.Path
            .of(dir.toString())
            .deleteRecursively()
    }

    private fun newStore() = CharacterConfigStore(configDir, fs)

    private fun readFile(path: Path): String = fs.source(path).buffered().use { it.readByteArray().decodeToString() }

    private fun actionsFile(characterId: String): Path {
        val (game, name) = characterId.split(":")
        return Path(Path(Path(Path(configDir, "characters"), game), name), "actions.toml")
    }

    @Test
    fun actions_roundTripWithToolbarAndGroup() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = ActionRepository(store)
            val char = "gs4:tholan"
            val look = Action(Uuid.random(), "Look", "put look")
            val attack = Action(Uuid.random(), "Attack", "put attack")
            val combat = Action(Uuid.random(), "Combat", script = null, children = listOf(attack.id))
            repo.saveAction(char, look)
            repo.saveAction(char, attack)
            repo.saveAction(char, combat)
            repo.setToolbar(char, listOf(look.id, combat.id))

            // A fresh store reads it back identically.
            val reloaded = newStore()
            reloaded.load()
            val bar = ActionRepository(reloaded).observeForCharacter(char).first()
            assertEquals(3, bar.actions.size)
            assertEquals(listOf("Look", "Combat"), bar.toolbar.map { it.name })
            val group = bar.toolbar.first { it.isGroup }
            assertEquals(listOf(attack.id), group.children)
            assertEquals("put attack", bar.actions[group.children.single()]?.script)
        }

    @Test
    fun actionsToml_isFlatWithInlineChildren() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = ActionRepository(store)
            val child = Action(Uuid.random(), "Child", "put a")
            val group = Action(Uuid.random(), "Group", script = null, children = listOf(child.id))
            repo.saveAction("gs4:tholan", child)
            repo.saveAction("gs4:tholan", group)
            repo.setToolbar("gs4:tholan", listOf(group.id))

            val text = readFile(actionsFile("gs4:tholan"))
            assertTrue(text.contains("toolbar = ["), "expected a toolbar array, was:\n$text")
            assertTrue(text.contains("[[actions]]"), "expected flat actions tables, was:\n$text")
            assertFalse(
                text.contains("[[actions.children]]"),
                "children should be inline ids, not nested tables:\n$text",
            )
            assertTrue(text.contains("children = ["), "expected inline children id array:\n$text")
        }

    @Test
    fun deleteAction_scrubsToolbarAndGroupReferences() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = ActionRepository(store)
            val char = "gs4:tholan"
            val child = Action(Uuid.random(), "Child", "put a")
            val group = Action(Uuid.random(), "Group", script = null, children = listOf(child.id))
            repo.saveAction(char, child)
            repo.saveAction(char, group)
            repo.setToolbar(char, listOf(child.id, group.id))

            repo.deleteAction(char, child.id)

            val bar = repo.observeForCharacter(char).first()
            assertFalse(bar.actions.containsKey(child.id))
            assertEquals(listOf(group.id), repo.observeToolbar(char).first())
            assertEquals(emptyList<Uuid>(), bar.actions[group.id]?.children)
        }

    @Test
    fun observeForCharacter_mergesGlobalAndCharacter_andSkipsDangling() =
        runBlocking {
            val store = newStore()
            store.load()
            val repo = ActionRepository(store)
            val globalAction = Action(Uuid.random(), "GlobalLook", "put look")
            val charAction = Action(Uuid.random(), "CharAttack", "put attack")
            repo.saveAction("global", globalAction)
            repo.setToolbar("global", listOf(globalAction.id))
            repo.saveAction("gs4:tholan", charAction)
            // Character toolbar references its own action plus a dangling id that must be skipped.
            repo.setToolbar("gs4:tholan", listOf(charAction.id, Uuid.random()))

            val bar = repo.observeForCharacter("gs4:tholan").first()
            assertTrue(bar.actions.containsKey(globalAction.id))
            assertTrue(bar.actions.containsKey(charAction.id))
            // Character toolbar first (dangling skipped), then global.
            assertEquals(listOf("CharAttack", "GlobalLook"), bar.toolbar.map { it.name })
        }
}
