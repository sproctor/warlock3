package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import warlockfe.warlock3.compose.util.HighlightIndex
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.util.SoundPlayer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Docking keeps each panel's content in a `SaveableStateProvider`, so moving, re-docking or switching
 * the tab of a window saves its saveable state and restores it when the window composes again. A
 * selection saved there comes back in a container that has not been laid out yet, and if it spans
 * rows, `SelectionContainer` asks for the container's coordinates during that first composition:
 * `IllegalArgumentException: null coordinates`, from the composition, which the uncaught handler
 * turns into a dead client. Even without the throw it would be anchored in selectable ids and
 * offsets of rows that have since changed.
 *
 * Nothing clears a selection on its way out of the composition unless focus leaves or a pinned row
 * is disposed, and a fuzzer found a window that hid with neither, then threw on coming back. Rather
 * than pin that sequence, this pins the property that rules it out, on the real window view: nothing
 * it offers the saveable state registry is a saved selection, so there is nothing to restore.
 */
class WindowSelectionRestoreTest {
    @Test
    fun theWindowViewDoesNotSaveItsSelection() {
        val registry = RecordingRegistry()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val scene = ImageComposeScene(width = 300, height = 200, density = Density(1f))
        try {
            val uiState =
                WindowUiState(
                    name = "main",
                    windowInfo = mutableStateOf(null),
                    style = StyleDefinition(),
                    data = StreamWindowData(stream(scope)),
                )
            scene.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    WindowViewScaffold(
                        uiState = uiState,
                        canHide = false,
                        defaultStyle = StyleDefinition(),
                        isSelected = true,
                        openWindows = listOf("main"),
                        menuData = null,
                        onActionClick = { null },
                        onCloseClick = {},
                        onSelect = {},
                        onOpenWindowSettings = {},
                        clearStream = {},
                        scrollEvents = emptyList(),
                        handledScrollEvent = {},
                        defaultFontSize = 14.sp,
                        surface = { modifier, content -> Box(modifier) { content() } },
                        header = { _, _ -> },
                        listContainer = { _, _, content -> content() },
                        actionContextMenu = { _, _, _ -> },
                        panelContent = { _, _, _, _, _ -> },
                    )
                }
            }
            scene.render()
            assertEquals(
                emptyList(),
                registry.savedSelections(),
                "the window view registered its selection for saving",
            )
        } finally {
            scene.close()
            scope.cancel()
        }
    }

    private fun stream(scope: CoroutineScope) =
        ComposeTextStream(
            id = "main",
            maxLines = 100,
            markLinks = false,
            showImages = false,
            showTimestamps = false,
            suppressPrompts = false,
            highlights = MutableStateFlow(HighlightIndex(emptyList())),
            names = MutableStateFlow(emptyList()),
            ignores = MutableStateFlow(emptyList()),
            alterations = MutableStateFlow(emptyList()),
            presets = MutableStateFlow(emptyMap()),
            monoFont = MutableStateFlow(null),
            soundPlayer =
                object : SoundPlayer {
                    override suspend fun playSound(filename: String): String? = null
                },
            workQueue = StreamWorkQueue(scope),
            scope = scope,
        )

    /**
     * Records what composition offers to save. A `SelectionState` goes in as the eight-entry list
     * `SelectionState.Saver` makes of it - the selected texts, then the start and end anchors and
     * whether the handles cross - which nothing else the window saves looks like.
     */
    private class RecordingRegistry : SaveableStateRegistry {
        private val providers = mutableMapOf<String, () -> Any?>()

        fun savedSelections(): List<String> =
            providers
                .filter { (_, provider) ->
                    val saved = provider()
                    saved is List<*> && saved.size == 8 && saved[0] is List<*>
                }.keys
                .toList()

        override fun canBeSaved(value: Any): Boolean = true

        override fun consumeRestored(key: String): Any? = null

        override fun performSave(): Map<String, List<Any?>> = emptyMap()

        override fun registerProvider(
            key: String,
            valueProvider: () -> Any?,
        ): SaveableStateRegistry.Entry {
            providers[key] = valueProvider
            return object : SaveableStateRegistry.Entry {
                override fun unregister() {
                    providers.remove(key)
                }
            }
        }
    }
}
