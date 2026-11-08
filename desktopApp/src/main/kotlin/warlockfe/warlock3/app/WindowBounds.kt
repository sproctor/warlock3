package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
)

/** A window's bounds at one moment, and the character connected in it, if any. */
internal data class WindowSnapshot(
    val characterId: String?,
    val bounds: MainWindowBounds,
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
 * [MIN_WINDOW_DIMENSION] is never saved. Runs until cancelled.
 */
@OptIn(FlowPreview::class)
internal suspend fun saveWindowBounds(
    snapshots: Flow<WindowSnapshot>,
    store: WindowBoundsStore,
) {
    coroutineScope {
        snapshots
            .map { it.bounds.width to it.bounds.height }
            .distinctUntilChanged()
            .debounce(WINDOW_BOUNDS_SAVE_DELAY)
            .onEach { (width, height) ->
                if (width >= MIN_WINDOW_DIMENSION) {
                    store.putDefaultWidth(width)
                }
                if (height >= MIN_WINDOW_DIMENSION) {
                    store.putDefaultHeight(height)
                }
            }.launchIn(this)
        snapshots
            .distinctUntilChanged()
            .debounce(WINDOW_BOUNDS_SAVE_DELAY)
            .onEach { (characterId, bounds) ->
                if (characterId != null && bounds.isUsable()) {
                    store.putCharacterBounds(characterId, bounds)
                }
            }.launchIn(this)
    }
}

/** The bounds saved for [characterId] that its window should take, or null to leave it as it is. */
internal suspend fun characterBoundsToRestore(
    characterId: String?,
    placement: GameWindowPlacement,
    store: WindowBoundsStore,
): MainWindowBounds? {
    if (placement.sizeFromCommandLine || characterId == null) return null
    return store.getCharacterBounds(characterId)?.takeIf { it.isUsable() }
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
        val bounds = characterBoundsToRestore(characterId, placement, store) ?: return@LaunchedEffect
        windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
        windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
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
