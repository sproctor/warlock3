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
 * platform puts it.
 */
internal fun newWindowPlacement(
    opener: WindowState,
    defaultSize: DpSize,
): GameWindowPlacement =
    GameWindowPlacement(
        size = if (opener.placement == WindowPlacement.Floating) opener.size else defaultSize,
        position = WindowPosition.PlatformDefault,
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

    suspend fun getCharacterBounds(characterId: String): MainWindowBounds?

    /** Saves [bounds] as [characterId]'s; with null x and y, keeps the position saved before. */
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
 * character's, each once the window has held still for [WINDOW_BOUNDS_SAVE_DELAY]. Nothing is saved
 * while the window is maximized or fullscreen, whose size is the screen's, and a dimension below
 * [MIN_WINDOW_DIMENSION] never is. The size the window opened at, [openingSize], is not saved as
 * the default until the window changes it: it is either saved already or came from the command
 * line, which is for that launch only. Runs until cancelled.
 */
@OptIn(FlowPreview::class)
internal suspend fun saveWindowBounds(
    snapshots: Flow<WindowSnapshot>,
    store: WindowBoundsStore,
    openingSize: DpSize,
) {
    var savedSize = openingSize.width.value.toInt() to openingSize.height.value.toInt()
    snapshots
        .distinctUntilChanged()
        .debounce(WINDOW_BOUNDS_SAVE_DELAY)
        .collect { (characterId, bounds, placement) ->
            if (placement != WindowPlacement.Floating) return@collect
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
            if (characterId != null && bounds.isUsable()) {
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
 * window as it is. A width, height or position the command line gave wins over the saved one.
 */
internal suspend fun characterBoundsToRestore(
    characterId: String?,
    placement: GameWindowPlacement,
    store: WindowBoundsStore,
): RestoredBounds? {
    if (characterId == null) return null
    val bounds = store.getCharacterBounds(characterId)?.takeIf { it.isUsable() } ?: return null
    val size =
        if (placement.widthFromCommandLine && placement.heightFromCommandLine) {
            null
        } else {
            DpSize(
                width = if (placement.widthFromCommandLine) placement.size.width else bounds.width.dp,
                height = if (placement.heightFromCommandLine) placement.size.height else bounds.height.dp,
            )
        }
    val x = bounds.x
    val y = bounds.y
    val position =
        if (placement.positionFromCommandLine || x == null || y == null) null else WindowPosition(x.dp, y.dp)
    return if (size == null && position == null) null else RestoredBounds(size, position)
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
            snapshots = snapshotFlow { windowState.snapshot(currentCharacterId) },
            store = store,
            openingSize = placement.size,
        )
    }
}
