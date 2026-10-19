package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
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
 * than pin that sequence, this pins the property that rules it out: the window's selection state
 * is never offered to the saveable state registry, so there is nothing to restore.
 */
class WindowSelectionRestoreTest {
    @Test
    fun theWindowSelectionIsNotSaved() {
        val registry = RecordingRegistry()
        val scene = ImageComposeScene(width = 100, height = 100, density = Density(1f))
        try {
            scene.setContent {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    rememberWindowSelectionState()
                }
            }
            scene.render()
            assertEquals(emptyList(), registry.registered, "the window selection state registered for saving")
        } finally {
            scene.close()
        }
    }

    private class RecordingRegistry : SaveableStateRegistry {
        val registered = mutableListOf<String>()

        override fun canBeSaved(value: Any): Boolean = true

        override fun consumeRestored(key: String): Any? = null

        override fun performSave(): Map<String, List<Any?>> = emptyMap()

        override fun registerProvider(
            key: String,
            valueProvider: () -> Any?,
        ): SaveableStateRegistry.Entry {
            registered += key
            return object : SaveableStateRegistry.Entry {
                override fun unregister() {
                    registered -= key
                }
            }
        }
    }
}
