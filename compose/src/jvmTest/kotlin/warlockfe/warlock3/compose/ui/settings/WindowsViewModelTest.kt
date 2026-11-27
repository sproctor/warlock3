package warlockfe.warlock3.compose.ui.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.deleteTestDir
import warlockfe.warlock3.compose.testAppContainer
import warlockfe.warlock3.core.client.GameCharacter
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Windows page places what the user asked to see only once the character's own settings are in
 * (see settleRevealRequests), so the view model must say when that is: no settings until they load,
 * then settings tagged with the character they belong to.
 */
class WindowsViewModelTest {
    private lateinit var dir: java.nio.file.Path
    private val store = ViewModelStore()

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("windows-view-model-test")
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        // Stops the view model's settings query before its database goes.
        store.clear()
        Dispatchers.resetMain()
        deleteTestDir(dir)
    }

    private fun viewModel(
        container: AppContainer,
        character: GameCharacter,
    ): WindowsViewModel =
        ViewModelProvider.create(
            store,
            viewModelFactory {
                initializer {
                    WindowsViewModel(
                        character,
                        container.characterRepository,
                        container.characterSettingsRepository,
                        container.windowSettingRepository,
                    )
                }
            },
        )[WindowsViewModel::class]

    private suspend fun WindowsViewModel.loadedFor(characterId: String): CharacterWindowSettings =
        withTimeout(10_000) { windowSettings.filterNotNull().first { it.characterId == characterId } }

    @Test
    fun settingsStartUnloadedThenArriveTaggedWithTheirCharacter() =
        runBlocking {
            val container = testAppContainer(dir, Dispatchers.IO)
            container.windowSettingRepository.setScale("a", "room", 1.2f)

            val viewModel = viewModel(container, GameCharacter("a", "DR", "Ann"))
            // An empty list here would read as "this character has no saved windows".
            assertNull(viewModel.windowSettings.value)

            assertEquals(listOf("room"), viewModel.loadedFor("a").settings.map { it.name })
        }

    @Test
    fun anotherCharactersSettingsAreTaggedWithThatCharacter() =
        runBlocking {
            val container = testAppContainer(dir, Dispatchers.IO)
            container.windowSettingRepository.setScale("a", "room", 1.2f)
            container.windowSettingRepository.setScale("b", "inv", 0.8f)

            val viewModel = viewModel(container, GameCharacter("a", "DR", "Ann"))
            viewModel.loadedFor("a")
            viewModel.selectCharacter(GameCharacter("b", "DR", "Bob"))

            assertEquals(listOf("inv"), viewModel.loadedFor("b").settings.map { it.name })
        }
}
