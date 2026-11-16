package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.compose.util.LatestValueWriter
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository

// The compose-docking layout JSON for this character, one blob per character. Written debounced on
// every layout change; a window it does not know yet docks at its game-announced location.
const val DOCK_LAYOUT_KEY = "dockLayout"

// Where windows were when they were closed. Separate from the arrangement rather than folded
// into it, so an unreadable one costs only the memory and every layout already saved still reads.
const val DOCK_SPOTS_KEY = "dockSpots"

/**
 * Saves and loads the connected character's docking layout: the arrangement of its windows, and
 * where each closed window was.
 *
 * The layout is auto-saved, so two writes that finished out of order would leave the older
 * arrangement stored with nothing to correct it. Both writers - the bridge's debounced save and
 * [flush] when the game closes - go through one [LatestValueWriter], which keeps the newest layout
 * the last one written. It lives outside the composition so [flush] can write a layout the debounce
 * has not gotten to: an arrangement changed within a second of quitting would otherwise never reach
 * the database, the composition being torn down first.
 */
internal class DockLayoutStore(
    private val characterId: StateFlow<String?>,
    private val characterSettingsRepository: CharacterSettingsRepository,
) {
    // These hold how to encode the latest layout and spots rather than the JSON itself: the layout
    // changes every frame of a drag, and only the encoding the debounce or the close gets to is
    // worth making. The spots encode to null when nothing changed since they were last saved.
    private val layoutWriter = LatestValueWriter<() -> String> { save(DOCK_LAYOUT_KEY, it()) }

    // Saved on the same beat as the arrangement, and for the same reason.
    private val spotsWriter = LatestValueWriter<() -> String?> { encode -> encode()?.let { save(DOCK_SPOTS_KEY, it) } }

    /** The saved docking-layout JSON, or null when none has been saved yet. */
    suspend fun loadLayout(): String? = load(DOCK_LAYOUT_KEY)

    /** The saved closed-window spots, or null when none have been saved yet. */
    suspend fun loadSpots(): String? = load(DOCK_SPOTS_KEY)

    /**
     * Records that the docking layout changed, with how to [encode] it as JSON for the next [flush]
     * to save. The caller records every change and flushes on a debounce, so a change the debounce
     * has not reached yet is still saved when the game closes.
     */
    fun recordLayout(encode: () -> String) {
        layoutWriter.record(encode)
    }

    /**
     * Records how to [encode] where closed windows were, or null when that has not changed, so
     * reopening puts them back in a later session too.
     */
    fun recordSpots(encode: () -> String?) {
        spotsWriter.record(encode)
    }

    /** Saves the layout and spots recorded since they were last saved. */
    suspend fun flush() {
        layoutWriter.flush()
        spotsWriter.flush()
    }

    private suspend fun load(key: String): String? =
        characterId.value?.let { characterId ->
            characterSettingsRepository.get(characterId, key)
        }

    private suspend fun save(
        key: String,
        value: String,
    ) {
        characterId.value?.let { characterId ->
            characterSettingsRepository.save(characterId, key, value)
        }
    }
}
