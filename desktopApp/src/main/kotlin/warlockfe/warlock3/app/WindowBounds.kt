package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.core.prefs.repositories.MainWindowBounds
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The smallest width or height, in dp, that a game window opens at or has its size saved from. */
internal const val MIN_WINDOW_DIMENSION = 240

/** How long a window has to hold still before its bounds are saved. */
internal val WINDOW_BOUNDS_SAVE_DELAY: Duration = 2.seconds

/** Where a game window opens, how big, and whether maximized. */
internal class GameWindowPlacement(
    /** The window's size, or with [maximized], the size it takes once unmaximized. */
    val size: DpSize,
    val position: WindowPosition,
    val maximized: Boolean,
    /**
     * The command line set the width, so it wins over the width saved for the character that
     * connects in the window.
     */
    val widthFromCommandLine: Boolean,
    /** The command line set the height; as [widthFromCommandLine]. */
    val heightFromCommandLine: Boolean,
    /**
     * The command line set the position, so it wins over the position saved for the character that
     * connects in the window.
     */
    val positionFromCommandLine: Boolean,
)

/**
 * Where a window opened from [opener]'s menu opens: at [opener]'s size while it is a normal window,
 * otherwise (maximized or fullscreen, whose size is the screen's) at [defaultSize]; wherever the
 * platform puts it, and not maximized.
 */
internal fun newWindowPlacement(
    opener: WindowState,
    defaultSize: DpSize,
): GameWindowPlacement =
    GameWindowPlacement(
        size = if (opener.placement == WindowPlacement.Floating) opener.size else defaultSize,
        position = WindowPosition.PlatformDefault,
        maximized = false,
        widthFromCommandLine = false,
        heightFromCommandLine = false,
        positionFromCommandLine = false,
    )

/** A window's bounds and placement at one moment, and the character connected in it, if any. */
internal data class WindowSnapshot(
    val characterId: String?,
    val bounds: MainWindowBounds,
    val placement: WindowPlacement,
)

/**
 * This window's [WindowSnapshot] now. The position is left out (null x and y) while the window has
 * none of its own: still where the platform put it, or on Wayland, which never says where a window
 * is. Its x and y are then Dp.Unspecified (NaN), which would save as 0.
 */
internal fun WindowState.snapshot(characterId: String?): WindowSnapshot {
    val absolute = position as? WindowPosition.Absolute
    return WindowSnapshot(
        characterId = characterId,
        bounds =
            MainWindowBounds(
                x = absolute?.x?.value?.toInt(),
                y = absolute?.y?.value?.toInt(),
                width = size.width.value.toInt(),
                height = size.height.value.toInt(),
            ),
        placement = placement,
    )
}

/** Where window sizes and per-character window bounds are kept. */
internal interface WindowBoundsStore {
    suspend fun putDefaultWidth(width: Int)

    suspend fun putDefaultHeight(height: Int)

    suspend fun putDefaultMaximized(maximized: Boolean)

    suspend fun getCharacterBounds(characterId: String): MainWindowBounds?

    /** Saves [bounds] as [characterId]'s; with null x and y, keeps the position saved before. */
    suspend fun putCharacterBounds(
        characterId: String,
        bounds: MainWindowBounds,
    )

    suspend fun getCharacterMaximized(characterId: String): Boolean

    suspend fun putCharacterMaximized(
        characterId: String,
        maximized: Boolean,
    )
}

internal fun AppContainer.windowBoundsStore(): WindowBoundsStore =
    object : WindowBoundsStore {
        override suspend fun putDefaultWidth(width: Int) = clientSettings.putWidth(width)

        override suspend fun putDefaultHeight(height: Int) = clientSettings.putHeight(height)

        override suspend fun putDefaultMaximized(maximized: Boolean) = clientSettings.putMaximized(maximized)

        override suspend fun getCharacterBounds(characterId: String) = characterSettingsRepository.getMainWindowBounds(characterId)

        override suspend fun putCharacterBounds(
            characterId: String,
            bounds: MainWindowBounds,
        ) = characterSettingsRepository.saveMainWindowBounds(characterId, bounds)

        override suspend fun getCharacterMaximized(characterId: String) = characterSettingsRepository.getMainWindowMaximized(characterId)

        override suspend fun putCharacterMaximized(
            characterId: String,
            maximized: Boolean,
        ) = characterSettingsRepository.saveMainWindowMaximized(characterId, maximized)
    }

/**
 * What one window's saving and restoring share beyond its [WindowState]:
 * - The default size and maximized state as the window knows them: as it last saved them, or as the
 *   app set them itself (the size the window opened at, a character's restored bounds). A window that
 *   settles on these saves nothing as the default, so only the user resizing or maximizing it changes
 *   what new windows open at.
 * - [floating], the window's bounds as a normal window, kept while it is maximized (when its size
 *   is the screen's) to be saved as the size it goes back to. Null while it has had none.
 */
internal class WindowBoundsMemory(
    openingSize: DpSize,
    var defaultMaximized: Boolean,
) {
    var defaultWidth = openingSize.width.value.toInt()
        private set
    var defaultHeight = openingSize.height.value.toInt()
        private set
    var floating: MainWindowBounds? = null

    fun setDefaultSize(
        width: Int,
        height: Int,
    ) {
        defaultWidth = width
        defaultHeight = height
    }
}

/** A window as it settles: [WindowBoundsMemory.floating] then, and whether it is maximized. */
private data class SettledWindow(
    val characterId: String?,
    val floating: MainWindowBounds?,
    val maximized: Boolean,
)

/**
 * Saves a window's size and maximized state as what new windows open at, and its bounds and
 * maximized state as the connected character's, each once the window has held still for
 * [WINDOW_BOUNDS_SAVE_DELAY]. A maximized window saves the size it had before it was maximized, not
 * the screen's; a fullscreen one saves nothing. A dimension below [MIN_WINDOW_DIMENSION] is never
 * saved, and no default [memory] already has is saved again. Runs until cancelled.
 */
@OptIn(FlowPreview::class)
internal suspend fun saveWindowBounds(
    snapshots: Flow<WindowSnapshot>,
    store: WindowBoundsStore,
    memory: WindowBoundsMemory,
) {
    snapshots
        .filter { it.placement != WindowPlacement.Fullscreen }
        // Before the debounce, so a resize just before maximizing is still kept.
        .map { snapshot ->
            if (snapshot.placement == WindowPlacement.Floating) memory.floating = snapshot.bounds
            SettledWindow(
                characterId = snapshot.characterId,
                floating = memory.floating,
                maximized = snapshot.placement == WindowPlacement.Maximized,
            )
        }.distinctUntilChanged()
        .debounce(WINDOW_BOUNDS_SAVE_DELAY)
        .collect { (characterId, floating, maximized) ->
            // A move alone leaves the size as it was saved.
            if (floating != null && (floating.width != memory.defaultWidth || floating.height != memory.defaultHeight)) {
                memory.setDefaultSize(floating.width, floating.height)
                if (floating.width >= MIN_WINDOW_DIMENSION) {
                    store.putDefaultWidth(floating.width)
                }
                if (floating.height >= MIN_WINDOW_DIMENSION) {
                    store.putDefaultHeight(floating.height)
                }
            }
            if (maximized != memory.defaultMaximized) {
                memory.defaultMaximized = maximized
                store.putDefaultMaximized(maximized)
            }
            if (characterId != null) {
                if (floating != null && floating.isUsable()) {
                    store.putCharacterBounds(characterId, floating)
                }
                store.putCharacterMaximized(characterId, maximized)
            }
        }
}

/** What of a character's saved bounds its window takes; a null part stays as it is. */
internal data class RestoredBounds(
    val width: Dp?,
    val height: Dp?,
    val position: WindowPosition?,
    val maximized: Boolean,
)

/**
 * The parts of the bounds saved for [characterId] that its window should take, or null to leave the
 * window as it is. A width, height or position the command line gave wins over the saved one, and a
 * size from the command line asks for a window of that size, so it is not maximized either.
 */
internal suspend fun characterBoundsToRestore(
    characterId: String?,
    placement: GameWindowPlacement,
    store: WindowBoundsStore,
): RestoredBounds? {
    if (characterId == null) return null
    val bounds = store.getCharacterBounds(characterId)?.takeIf { it.isUsable() }
    val x = bounds?.x
    val y = bounds?.y
    val restored =
        RestoredBounds(
            width = bounds?.width?.dp?.takeUnless { placement.widthFromCommandLine },
            height = bounds?.height?.dp?.takeUnless { placement.heightFromCommandLine },
            position =
                if (placement.positionFromCommandLine || x == null || y == null) null else WindowPosition(x.dp, y.dp),
            maximized =
                !placement.widthFromCommandLine &&
                    !placement.heightFromCommandLine &&
                    store.getCharacterMaximized(characterId),
        )
    return restored.takeUnless { it == RestoredBounds(width = null, height = null, position = null, maximized = false) }
}

/**
 * Gives [windowState] what [characterId]'s saved bounds say (see [characterBoundsToRestore]), unless
 * the window is maximized or fullscreen, which the user chose for it. What it sets goes into
 * [memory], so that it is not saved as the default, and so that a window it maximizes keeps the
 * restored bounds as the ones it goes back to.
 */
internal suspend fun restoreCharacterBounds(
    windowState: WindowState,
    characterId: String?,
    placement: GameWindowPlacement,
    store: WindowBoundsStore,
    memory: WindowBoundsMemory,
) {
    if (windowState.placement != WindowPlacement.Floating) return
    val restored = characterBoundsToRestore(characterId, placement, store) ?: return
    restored.position?.let { windowState.position = it }
    windowState.size = DpSize(restored.width ?: windowState.size.width, restored.height ?: windowState.size.height)
    val bounds = windowState.snapshot(characterId).bounds
    memory.floating = bounds
    memory.setDefaultSize(bounds.width, bounds.height)
    if (restored.maximized) {
        windowState.placement = WindowPlacement.Maximized
        memory.defaultMaximized = true
    }
}

private fun MainWindowBounds.isUsable() = width >= MIN_WINDOW_DIMENSION && height >= MIN_WINDOW_DIMENSION

/**
 * Keeps [windowState] and the saved bounds in step: restores the connected character's saved bounds
 * when a character connects (see [restoreCharacterBounds]), and saves the window's as it changes
 * (see [saveWindowBounds]).
 */
@Composable
internal fun WindowBoundsPersistence(
    windowState: WindowState,
    characterId: String?,
    placement: GameWindowPlacement,
    store: WindowBoundsStore,
) {
    val memory = remember(windowState) { WindowBoundsMemory(placement.size, placement.maximized) }
    LaunchedEffect(windowState, characterId) {
        restoreCharacterBounds(windowState, characterId, placement, store, memory)
    }
    val currentCharacterId by rememberUpdatedState(characterId)
    LaunchedEffect(windowState, store) {
        saveWindowBounds(
            snapshots = snapshotFlow { windowState.snapshot(currentCharacterId) },
            store = store,
            memory = memory,
        )
    }
}
