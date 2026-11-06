package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.desktop.ui.game.DesktopDockHost
import warlockfe.warlock3.compose.desktop.ui.game.DetachedGameWindows
import warlockfe.warlock3.compose.desktop.ui.game.LocalDesktopDockHost
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopCrashReportingNotice
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.rememberSafeClipboard
import warlockfe.warlock3.core.prefs.repositories.MainWindowBounds
import warlockfe.warlock3.core.sge.SgeSettings
import kotlin.time.Duration.Companion.seconds

/** The smallest width or height, in dp, that a game window opens at or has its size saved from. */
internal const val MIN_WINDOW_DIMENSION = 240

/** How a game window opens, from the command line and the size the last session saved. */
internal class GameWindowPlacement(
    val size: DpSize,
    val position: WindowPosition,
    /**
     * The command line set the size, so it wins over the size saved for the character that
     * connects in the window.
     */
    val sizeFromCommandLine: Boolean,
)

/**
 * One game's top-level window, with the windows torn off its dock beside it. Closing it closes its
 * game, and closing the last one quits the app.
 *
 * @param showCrashReportingNotice whether this window asks the crash-reporting question; only one
 *   window should.
 * @param onDisconnect when not null, the window closes as soon as its connection drops (e.g. an
 *   input file replayed to EOF) instead of offering to reconnect, and this runs first.
 */
@Composable
internal fun NucleusApplicationScope.GameWindow(
    gameState: GameState,
    games: SnapshotStateList<GameState>,
    appContainer: AppContainer,
    sgeSettings: SgeSettings,
    placement: GameWindowPlacement,
    icon: Painter,
    skin: Map<String, SkinObject>,
    warlockVersion: String,
    showCrashReportingNotice: Boolean,
    onCrashReportingNoticeDismiss: () -> Unit,
    onDisconnect: (() -> Unit)?,
    showUpdateDialog: () -> Unit,
) {
    val windowState =
        remember {
            WindowState(
                width = placement.size.width,
                height = placement.size.height,
                position = placement.position,
            )
        }
    // Where this window's game screen publishes its dock state.
    val dockHost = remember { DesktopDockHost() }
    // This window's own view-model store, cleared when the game closes. A Tao window, unlike an AWT
    // one, gives its scene no store of its own: every window falls back to one process-wide store,
    // where a second window's dashboard got the first window's view model, bound to the first
    // window's game, and connecting from it took that window over.
    val viewModelStoreOwner = remember { GameWindowViewModelStoreOwner() }
    DisposableEffect(viewModelStoreOwner) {
        onDispose { viewModelStoreOwner.viewModelStore.clear() }
    }
    val subtitle by gameState.getTitle().collectAsState("loading")
    val title = "Warlock - $subtitle"
    val connectedScreen = gameState.screen as? GameScreen.ConnectedGameState
    val characterFlow = connectedScreen?.viewModel?.character ?: flowOf(null)
    val connectedCharacter by characterFlow.collectAsState(null)
    val scope = rememberCoroutineScope()

    // Close just this window; tear the whole app down only once the last one is gone.
    fun closeGame() {
        scope.launch {
            val screen = gameState.screen
            if (screen is GameScreen.ConnectedGameState) {
                try {
                    screen.viewModel.close()
                } catch (_: Throwable) {
                    // Ignore failures
                }
            }
            games.remove(gameState)
            if (games.isEmpty()) {
                exitApplication()
            }
        }
    }
    if (onDisconnect != null) {
        val currentOnDisconnect by rememberUpdatedState(onDisconnect)
        val disconnectedFlow = connectedScreen?.viewModel?.disconnected ?: flowOf(false)
        val disconnected by disconnectedFlow.collectAsState(false)
        LaunchedEffect(disconnected) {
            if (disconnected) {
                currentOnDisconnect()
                closeGame()
            }
        }
    }
    DecoratedWindow(
        title = title,
        state = windowState,
        icon = icon,
        onCloseRequest = { closeGame() },
        minimumSize = DpSize(MIN_WINDOW_DIMENSION.dp, MIN_WINDOW_DIMENSION.dp),
        onPreviewKeyEvent = dockHost::cancelDragOnEscape,
    ) {
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides viewModelStoreOwner,
            LocalSkin provides skin,
            LocalDesktopDockHost provides dockHost,
            // A copy that cannot open the system clipboard must not take the app down with it;
            // see [SafeClipboard].
            LocalClipboard provides rememberSafeClipboard(),
        ) {
            if (showCrashReportingNotice) {
                DesktopCrashReportingNotice(
                    crashReporting = appContainer.crashReporting,
                    onDismiss = onCrashReportingNoticeDismiss,
                )
            }
            WarlockApp(
                title = title,
                warlockVersion = warlockVersion,
                appContainer = appContainer,
                gameState = gameState,
                games = games,
                openNewWindow = {
                    // A manually opened window shows the dashboard; never auto-connect into it.
                    games.add(GameState(startupConnection = null))
                },
                showUpdateDialog = showUpdateDialog,
                sgeSettings = sgeSettings,
            )
            WindowBoundsPersistence(
                windowState = windowState,
                characterId = connectedCharacter?.id,
                restoreCharacterBounds = !placement.sizeFromCommandLine,
                appContainer = appContainer,
            )
        }
    }
    // The windows torn off this window's dock, alongside it rather than inside it. Draws nothing
    // until the user detaches something.
    DetachedGameWindows(
        dockHost = dockHost,
        viewModel = connectedScreen?.viewModel,
        skin = skin,
    )
}

/**
 * Saves the window's size as the size new windows open at, and its bounds as the connected
 * character's, each once the window has held still for a moment. When a character connects, its
 * saved bounds are restored, unless [restoreCharacterBounds] is false.
 */
@OptIn(FlowPreview::class)
@Composable
private fun WindowBoundsPersistence(
    windowState: WindowState,
    characterId: String?,
    restoreCharacterBounds: Boolean,
    appContainer: AppContainer,
) {
    val clientSettings = appContainer.clientSettings
    val characterSettings = appContainer.characterSettingsRepository
    LaunchedEffect(windowState, characterId) {
        if (!restoreCharacterBounds) return@LaunchedEffect
        if (characterId == null) return@LaunchedEffect
        val bounds = characterSettings.getMainWindowBounds(characterId) ?: return@LaunchedEffect
        if (bounds.width >= MIN_WINDOW_DIMENSION && bounds.height >= MIN_WINDOW_DIMENSION) {
            windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
            windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
        }
    }
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }
            .debounce(2.seconds)
            .onEach { size ->
                val width = size.width.value.toInt()
                if (width >= MIN_WINDOW_DIMENSION) {
                    clientSettings.putWidth(width)
                }
                val height = size.height.value.toInt()
                if (height >= MIN_WINDOW_DIMENSION) {
                    clientSettings.putHeight(height)
                }
            }.launchIn(this)
    }
    LaunchedEffect(windowState, characterId) {
        if (characterId == null) return@LaunchedEffect
        snapshotFlow {
            val position = windowState.position
            val size = windowState.size
            MainWindowBounds(
                x = position.x.value.toInt(),
                y = position.y.value.toInt(),
                width = size.width.value.toInt(),
                height = size.height.value.toInt(),
            )
        }.debounce(2.seconds)
            .onEach { bounds ->
                if (bounds.width >= MIN_WINDOW_DIMENSION && bounds.height >= MIN_WINDOW_DIMENSION) {
                    characterSettings.saveMainWindowBounds(characterId, bounds)
                }
            }.launchIn(this)
    }
}

internal fun GameState.getTitle(): Flow<String> =
    when (val screen = this.screen) {
        GameScreen.Dashboard -> {
            flow { emit("Dashboard") }
        }

        is GameScreen.ConnectedGameState -> {
            combine(
                screen.viewModel.windowTitle,
                screen.viewModel.character,
            ) { windowTitle, character ->
                windowTitle ?: character?.name ?: "Loading..."
            }
        }

        is GameScreen.NewGameState -> {
            flow { emit("New game") }
        }

        is GameScreen.ErrorState -> {
            flow { emit("Error") }
        }
    }

/**
 * One game window's view-model store. Held by the application composition, outside the window,
 * which has no host to take a store from, so it is a plain store rather than one remembered from a
 * parent.
 */
private class GameWindowViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
