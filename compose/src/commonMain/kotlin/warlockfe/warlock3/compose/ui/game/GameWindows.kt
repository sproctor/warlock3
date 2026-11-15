package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.mutableStateOf
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import warlockfe.warlock3.compose.ui.window.ComposePanelState
import warlockfe.warlock3.compose.ui.window.ComposeTextStream
import warlockfe.warlock3.compose.ui.window.PanelWindowData
import warlockfe.warlock3.compose.ui.window.StreamWindowData
import warlockfe.warlock3.compose.ui.window.WindowData
import warlockfe.warlock3.compose.ui.window.WindowUiState
import warlockfe.warlock3.compose.ui.window.getStyle
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.models.WindowSettings
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.core.window.WindowType
import kotlin.time.Duration.Companion.seconds

/**
 * The game's windows as the screen shows them: the main window, and every other window that is
 * open, in restore/open order. The docking bridge reconciles the dock tree from [windowUiStates];
 * where each window docks is the layout JSON's business, with the game-announced location as the
 * seed for windows the JSON does not know.
 *
 * Windows open and close at the user's hand and at the game's, and each one's open flag is saved
 * per character so the character's layout comes back on the next connect.
 */
internal class GameWindows(
    private val client: WarlockClient,
    private val windowRegistry: WindowRegistry,
    private val windowSettingsRepository: WindowSettingsRepository,
    private val windowSettings: StateFlow<List<WindowSettings>>,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("GameWindows")

    private val windows = client.windowInfo

    // The skin's named-color palette, so a window's skin-referenced text/background color resolves
    // (see WindowSettings.getStyle) instead of staying stuck at its last-saved literal.
    private val colorPalette = windowRegistry.colorPalette

    private val _windowUiStates = MutableStateFlow<List<WindowUiState>>(emptyList())
    val windowUiStates: StateFlow<List<WindowUiState>> = _windowUiStates.asStateFlow()

    private val _mainWindowUiState =
        MutableStateFlow(
            WindowUiState(
                name = MAIN_WINDOW_NAME,
                windowInfo = mutableStateOf(windows.value.firstOrNull { it.name == MAIN_WINDOW_NAME }),
                // Unspecified, not a concrete style: a window with nothing saved has to inherit the
                // base style, and mergeWith keeps whatever this one specifies.
                style = StyleDefinition(),
                data =
                    StreamWindowData(
                        stream = windowRegistry.getOrCreateStream(MAIN_WINDOW_NAME) as ComposeTextStream,
                    ),
            ),
        )
    val mainWindowUiState: StateFlow<WindowUiState> = _mainWindowUiState.asStateFlow()

    // On-demand window ui states for the mobile phone/tablet stream tabs, keyed by window name, so
    // switching tabs reuses the same stream and scroll state instead of rebuilding each time.
    private val tabWindowUiStates = mutableMapOf<String, WindowUiState>()

    // Completed once the saved window layout has been restored into the dock lists (also on a
    // failed restore - see restoreLayoutOnConnect). A Deferred rather than a resettable flag: the
    // dock lists are never cleared, so "restored" can never become false again. Game-announced
    // opens and closes wait on it so they land after the saved layout instead of racing it into
    // the docks.
    private val layoutRestored = CompletableDeferred<Unit>()

    // Stamp of the game's latest open/close instruction per window, taken while the event
    // collector is still synchronous. The handlers suspend (on the character id and the layout
    // restore) before acting, so each re-checks that it still holds the newest instruction and
    // drops itself otherwise - without this, an open parked on the restore could land after the
    // close that superseded it.
    private var gameWindowEventCounter = 0L
    private val latestGameWindowEvent = mutableMapOf<String, Long>()

    init {
        restoreLayoutOnConnect()
        applySettingsChanges()
    }

    /**
     * Suspends until the character's saved open windows have been loaded into [windowUiStates].
     *
     * The docking bridge waits on this before it reconciles a restored arrangement, for the same
     * reason [openFromGame] does. Both it and [restoreLayoutOnConnect] wake on the same character id
     * emission with nothing ordering them, and a reconcile against a window list that is still
     * empty undocks every window the arrangement just restored.
     */
    suspend fun awaitRestored() {
        layoutRestored.await()
    }

    private fun stampGameWindowEvent(name: String): Long {
        val stamp = ++gameWindowEventCounter
        latestGameWindowEvent[name] = stamp
        return stamp
    }

    /** Build the [WindowData] for a window of the given [windowType], or null if it carries none. */
    private fun createWindowData(
        windowType: WindowType?,
        name: String,
    ): WindowData? =
        when (windowType) {
            WindowType.STREAM -> StreamWindowData(windowRegistry.getOrCreateStream(name) as ComposeTextStream)
            WindowType.PANEL -> PanelWindowData(windowRegistry.getOrCreatePanel(name) as ComposePanelState)
            else -> null
        }

    /** A fresh ui state for window [name], styled by its saved [settings] when it has any. */
    private fun buildWindowUiState(
        name: String,
        settings: WindowSettings?,
    ): WindowUiState {
        val windowInfo = windows.value.firstOrNull { it.name == name }
        return WindowUiState(
            name = name,
            windowInfo = mutableStateOf(windowInfo),
            style = settings?.getStyle(colorPalette.value) ?: StyleDefinition(),
            font = settings?.font,
            monoFont = settings?.monoFont,
            scale = settings?.scale,
            nameFilter = settings?.nameFilter ?: false,
            data = createWindowData(windowInfo?.windowType, name),
        )
    }

    private fun savedSettings(name: String): WindowSettings? = windowSettings.value.firstOrNull { it.name == name }

    // Restore the saved window layout for a character when it connects.
    private fun restoreLayoutOnConnect() {
        client.characterId
            .onEach { characterId ->
                if (characterId != null) {
                    try {
                        val settings = windowSettingsRepository.observeWindowSettings(characterId).first()
                        settings.filter { it.open }.forEach { entity ->
                            // A user open, or a game open that gave up waiting on this restore,
                            // can land first - never add a second copy.
                            if (_windowUiStates.value.any { it.name == entity.name }) {
                                return@forEach
                            }
                            logger.d { "Loading entity: $entity" }
                            val uiState = buildWindowUiState(entity.name, entity)
                            (uiState.data as? StreamWindowData)?.stream?.setNameFilter(entity.nameFilter)
                            if (entity.name == MAIN_WINDOW_NAME) {
                                _mainWindowUiState.value = uiState
                            } else {
                                _windowUiStates.update { it + uiState }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Swallowing keeps the collector alive for the next connection and off
                        // the scope's unhandled path (a crash on Android).
                        logger.w(e) { "Failed to restore the window layout" }
                    } finally {
                        // Released even when the restore failed: parked opens append after the
                        // dock's saved positions (computed in the DB), so proceeding against a
                        // half-populated layout cannot corrupt it, while holding the gate would
                        // stall every panel on its timeout.
                        layoutRestored.complete(Unit)
                    }
                }
            }.launchIn(scope)
    }

    // Re-apply window styling and name filters whenever the saved window settings change.
    private fun applySettingsChanges() {
        windowSettings
            .onEach { currentWindowSettings ->
                currentWindowSettings.forEach { settings ->
                    if (settings.name == MAIN_WINDOW_NAME) {
                        _mainWindowUiState.update { it.withSettings(settings) }
                    } else {
                        _windowUiStates.update { states ->
                            val index = states.indexOfFirst { it.name == settings.name }
                            if (index != -1) {
                                val mutableStates = states.toMutableList()
                                mutableStates[index] = states[index].withSettings(settings)
                                mutableStates
                            } else {
                                states
                            }
                        }
                    }
                }
            }.launchIn(scope)
    }

    private fun WindowUiState.withSettings(settings: WindowSettings): WindowUiState {
        (data as? StreamWindowData)?.stream?.setNameFilter(settings.nameFilter)
        return copy(
            style = settings.getStyle(colorPalette.value),
            font = settings.font,
            monoFont = settings.monoFont,
            scale = settings.scale,
            nameFilter = settings.nameFilter,
        )
    }

    /** The game describing a window, which may have arrived after the window was opened. */
    fun onWindowInfo(info: WindowInfo) {
        if (info.name == MAIN_WINDOW_NAME) {
            _mainWindowUiState.value.windowInfo.value = info
            return
        }
        // A non-resident panel persists nothing, but one announced late can have picked up a saved
        // row before its info arrived (canSave is true for unknown windows). Clear it, or the
        // restore and every reorder keep tracking a window that saves nothing.
        if (!info.resident) {
            scope.launch {
                client.characterId.value?.let { characterId ->
                    windowSettingsRepository.removeWindowFromLayout(characterId, info.name)
                }
            }
        }
        _windowUiStates.value
            .indexOfFirst { it.name == info.name }
            .takeIf { it != -1 }
            ?.let { index ->
                val uiState = _windowUiStates.value[index]
                uiState.windowInfo.value = info
                if (uiState.data == null) {
                    _windowUiStates.update { states ->
                        val mutableStates = states.toMutableList()
                        mutableStates[index] =
                            uiState.copy(
                                data = createWindowData(info.windowType, info.name),
                            )
                        (mutableStates[index].data as? StreamWindowData)
                            ?.stream
                            ?.setNameFilter(uiState.nameFilter)
                        mutableStates
                    }
                }
            }
    }

    /**
     * The user opening a window. Its dock comes from the docking layout JSON when it is in there,
     * else from the game-announced location (see the bridge in GameDocking.kt); this only puts the
     * window in the layout. The list updates before anything suspends - normally in the same frame
     * as the click - so a close arriving afterwards always finds it there, and the screen and the
     * saved state cannot disagree about whether it is open.
     */
    fun open(name: String) {
        if (!place(name)) return
        saveOpen(name)
    }

    /**
     * Opens a window because the game asked for it with an `openDialog` tag. Where it lands is the
     * docking bridge's business; a panel the game did not place goes to the same spot the real
     * client floats an unplaced one, and one bound for the client's own chrome never gets here (see
     * ClientOpenWindowEvent).
     *
     * A window the user closed is left closed. The game announces its panels on every login, so
     * without that a panel the user does not want would come back every time they connect.
     */
    fun openFromGame(name: String) {
        if (name == _mainWindowUiState.value.name) return
        val stamp = stampGameWindowEvent(name)
        scope.launch {
            // The game announces most of its windows before naming the character (GS4 sends the
            // whole panel list ahead of the <app> tag), and the hidden check below is meaningless
            // without the character's saved state - sampling a still-null id here is what used to
            // reopen hidden panels on every GS4 login. Wait for the id; the timeout only covers a
            // server that never identifies the character at all.
            val characterId =
                withTimeoutOrNull(10.seconds) { client.characterId.filterNotNull().first() }
            if (characterId != null) {
                // The layout restore wakes on this same characterId emission; let it finish so
                // this window lands after the saved layout instead of racing it into the dock.
                if (withTimeoutOrNull(10.seconds) { layoutRestored.await() } == null) {
                    logger.w { "Layout restore still running after 10s; opening $name against it" }
                }
                // A window the user closed stays closed until they ask for it back.
                if (windowSettingsRepository.isHidden(characterId, name)) return@launch
            }
            // Superseded by a later open or close for this window while suspended above.
            if (latestGameWindowEvent[name] != stamp) return@launch
            if (!place(name)) return@launch
            saveOpen(name)
        }
    }

    private fun saveOpen(name: String) {
        if (!canSave(name)) return
        scope.launch {
            client.characterId.value?.let { characterId ->
                windowSettingsRepository.openWindow(characterId = characterId, name = name)
            }
        }
    }

    /**
     * Adds a window to the open list and returns whether it was added. Saves nothing: callers own
     * the saved open flag. Where the window docks is the bridge's business.
     */
    private fun place(name: String): Boolean {
        // A window lives in the list exactly once: an open for one already there is a no-op
        // (the visibility toggles route those to close).
        if (_windowUiStates.value.any { it.name == name }) return false
        var newState: WindowUiState? = null
        _windowUiStates.update { states ->
            // Reset on entry: update{} may retry, and a retry can take the other branch.
            newState = null
            if (states.any { it.name == name }) {
                states
            } else {
                val added = buildWindowUiState(name, savedSettings(name))
                newState = added
                states + added
            }
        }
        val state = newState ?: return false
        (state.data as? StreamWindowData)?.stream?.setNameFilter(state.nameFilter)
        return true
    }

    /**
     * Whether a window's layout may be written to the character's settings. A transient panel is
     * still moved, resized and closed in the running layout, it just leaves nothing behind. Windows
     * the game has not told us about are treated as savable, so a stream window still restores where
     * the user put it if its info has not arrived yet.
     */
    private fun canSave(name: String): Boolean = windows.value.firstOrNull { it.name == name }?.resident != false

    /**
     * The user closing a window. It is remembered as hidden, so the game cannot bring it back with an
     * `openDialog` - the user has to ask for it again.
     */
    fun close(name: String) {
        remove(name = name, hide = true)
    }

    /**
     * A panel's own `closeButton`. The button's command and the dismissal are one operation rather
     * than two actions, because whether the panel closes depends on what the command found: the
     * command always runs, and the close only follows for a non-resident panel.
     *
     * A resident panel is a permanent fixture of the character's layout, and the real client refuses
     * to close one rather than hiding it (it answers `_error Cannot close a resident dialog (<id>)`
     * upstream). The button's command still runs either way.
     */
    fun closeFromPanelButton(action: WarlockAction.ClosePanel) {
        val resident = windows.value.firstOrNull { it.name == action.id }?.resident == true
        scope.launch {
            action.command?.let { client.sendWidgetCommand(it, action.echo) }
            if (resident) {
                client.debug("Cannot close a resident dialog (${action.id})")
                return@launch
            }
            // Same path as the window's own close button, so the panel is remembered hidden and the
            // game cannot reopen it unasked.
            close(action.id)
        }
    }

    /**
     * The game closing a panel with a `closeDialog` tag. It leaves the layout but is not hidden: the
     * game is free to open it again later.
     *
     * Closes wait on the same gate as game opens: one that ran mid-restore would remove nothing
     * from the not-yet-populated dock while nulling the saved row underneath it, and a close
     * running ahead of a parked open for the same window would invert the protocol's order (the
     * stamp check settles who acts).
     */
    fun closeFromGame(name: String) {
        val stamp = stampGameWindowEvent(name)
        scope.launch {
            // No character means no restore is pending (it only runs for an identified
            // character), and the gate would never complete - act immediately.
            if (client.characterId.value != null) {
                if (withTimeoutOrNull(10.seconds) { layoutRestored.await() } == null) {
                    logger.w { "Layout restore still running after 10s; closing $name against it" }
                }
            }
            if (latestGameWindowEvent[name] != stamp) return@launch
            remove(name = name, hide = false)
        }
    }

    private fun remove(
        name: String,
        hide: Boolean,
    ) {
        _windowUiStates.update { states -> states.filter { it.name != name } }
        if (!canSave(name)) return
        scope.launch {
            client.characterId.value?.let { characterId ->
                if (hide) {
                    windowSettingsRepository.closeWindow(characterId = characterId, name = name)
                } else {
                    windowSettingsRepository.removeWindowFromLayout(characterId = characterId, name = name)
                }
            }
        }
    }

    /**
     * A [WindowUiState] for any window by [name], built on demand from the window registry and the
     * saved per-window settings. Lets the mobile phone/tablet tab layouts render a stream without
     * "opening" it into a dock. The main window returns its canonical, event-updated ui state.
     */
    fun streamWindowUiState(name: String): WindowUiState {
        if (name == MAIN_WINDOW_NAME) return _mainWindowUiState.value
        return tabWindowUiStates.getOrPut(name) { buildWindowUiState(name, savedSettings(name)) }
    }
}
