package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.core.prefs.repositories.MainWindowBounds
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The smallest width or height, in dp, that a game window opens at or has its size saved from. */
internal const val MIN_WINDOW_DIMENSION = 240

/** How long a window has to hold still before its bounds are saved. */
internal val WINDOW_BOUNDS_SAVE_DELAY: Duration = 2.seconds

/** Where a game window opens and how big. */
internal class GameWindowPlacement(
    val size: DpSize,
    val position: WindowPosition,
    /**
     * The command line set the size, so it wins over the size saved for the character that
     * connects in the window.
     */
    val sizeFromCommandLine: Boolean,
    /**
     * The command line set the position, so it wins over the position saved for the character that
     * connects in the window.
     */
    val positionFromCommandLine: Boolean,
)

/**
 * Where a window opened from [opener]'s menu opens: at [opener]'s size while it is a normal window,
 * otherwise (maximized or fullscreen, whose size is the screen's) at [defaultSize]; wherever the
 * platform puts it.
 */
internal fun newWindowPlacement(
    opener: WindowState,
    defaultSize: DpSize,
): GameWindowPlacement =
    GameWindowPlacement(
        size = if (opener.placement == WindowPlacement.Floating) opener.size else defaultSize,
        position = WindowPosition.PlatformDefault,
        sizeFromCommandLine = false,
        positionFromCommandLine = false,
    )

/**
 * A window's bounds at one moment, and the character connected in it, if any. [positioned] is false
 * while the window has no absolute position yet (still where the platform put it), when the x and y
 * in [bounds] mean nothing.
 */
internal data class WindowSnapshot(
    val characterId: String?,
    val bounds: MainWindowBounds,
    val positioned: Boolean = true,
)

/** Where window sizes and per-character window bounds are kept. */
internal interface WindowBoundsStore {
    suspend fun putDefaultWidth(width: Int)

    suspend fun putDefaultHeight(height: Int)

    suspend fun getCharacterBounds(characterId: String): MainWindowBounds?

    suspend fun putCharacterBounds(
        characterId: String,
        bounds: MainWindowBounds,
    )
}

internal fun AppContainer.windowBoundsStore(): WindowBoundsStore =
    object : WindowBoundsStore {
        override suspend fun putDefaultWidth(width: Int) = clientSettings.putWidth(width)

        override suspend fun putDefaultHeight(height: Int) = clientSettings.putHeight(height)

        override suspend fun getCharacterBounds(characterId: String) = characterSettingsRepository.getMainWindowBounds(characterId)

        override suspend fun putCharacterBounds(
            characterId: String,
            bounds: MainWindowBounds,
        ) = characterSettingsRepository.saveMainWindowBounds(characterId, bounds)
    }

/**
 * Saves a window's size as the size new windows open at, and its bounds as the connected
 * character's, each once the window has held still for [WINDOW_BOUNDS_SAVE_DELAY]. A dimension below
 * [MIN_WINDOW_DIMENSION] is never saved, nor a character's bounds while the window has no position.
 * Runs until cancelled.
 */
@OptIn(FlowPreview::class)
internal suspend fun saveWindowBounds(
    snapshots: Flow<WindowSnapshot>,
    store: WindowBoundsStore,
) {
    var savedSize: Pair<Int, Int>? = null
    snapshots
        .distinctUntilChanged()
        .debounce(WINDOW_BOUNDS_SAVE_DELAY)
        .collect { (characterId, bounds, positioned) ->
            // A move alone leaves the size as it was saved.
            val size = bounds.width to bounds.height
            if (size != savedSize) {
                savedSize = size
                if (bounds.width >= MIN_WINDOW_DIMENSION) {
                    store.putDefaultWidth(bounds.width)
                }
                if (bounds.height >= MIN_WINDOW_DIMENSION) {
                    store.putDefaultHeight(bounds.height)
                }
            }
            if (characterId != null && positioned && bounds.isUsable()) {
                store.putCharacterBounds(characterId, bounds)
            }
        }
}

/** What of a character's saved bounds its window takes; a null part stays as it is. */
internal data class RestoredBounds(
    val size: DpSize?,
    val position: WindowPosition?,
)

/**
 * The parts of the bounds saved for [characterId] that its window should take, or null to leave the
 * window as it is. A size or position the command line gave wins over the saved one.
 */
internal suspend fun characterBoundsToRestore(
    characterId: String?,
    placement: GameWindowPlacement,
    store: WindowBoundsStore,
): RestoredBounds? {
    if (characterId == null) return null
    if (placement.sizeFromCommandLine && placement.positionFromCommandLine) return null
    val bounds = store.getCharacterBounds(characterId)?.takeIf { it.isUsable() } ?: return null
    return RestoredBounds(
        size = DpSize(bounds.width.dp, bounds.height.dp).takeUnless { placement.sizeFromCommandLine },
        position = WindowPosition(bounds.x.dp, bounds.y.dp).takeUnless { placement.positionFromCommandLine },
    )
}

private fun MainWindowBounds.isUsable() = width >= MIN_WINDOW_DIMENSION && height >= MIN_WINDOW_DIMENSION

/**
 * Keeps [windowState] and the saved bounds in step: restores the connected character's saved bounds
 * when a character connects, and saves the window's as it changes (see [saveWindowBounds]).
 */
@Composable
internal fun WindowBoundsPersistence(
    windowState: WindowState,
    characterId: String?,
    placement: GameWindowPlacement,
    store: WindowBoundsStore,
) {
    LaunchedEffect(windowState, characterId) {
        val restored = characterBoundsToRestore(characterId, placement, store) ?: return@LaunchedEffect
        restored.size?.let { windowState.size = it }
        restored.position?.let { windowState.position = it }
    }
    val currentCharacterId by rememberUpdatedState(characterId)
    LaunchedEffect(windowState, store) {
        saveWindowBounds(
            snapshots =
                snapshotFlow {
                    val position = windowState.position
                    val size = windowState.size
                    WindowSnapshot(
                        characterId = currentCharacterId,
                        // Until then, x and y are Dp.Unspecified (NaN), which would save as 0.
                        positioned = position is WindowPosition.Absolute,
                        bounds =
                            MainWindowBounds(
                                x = position.x.value.toInt(),
                                y = position.y.value.toInt(),
                                width = size.width.value.toInt(),
                                height = size.height.value.toInt(),
                            ),
                    )
                },
            store = store,
        )
    }
}
