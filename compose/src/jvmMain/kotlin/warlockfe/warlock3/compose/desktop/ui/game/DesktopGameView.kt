package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.jewel.JewelDocking
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.ui.DockArea
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockAlertDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.generated.resources.circle
import warlockfe.warlock3.compose.generated.resources.circle_filled
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.game.MAIN_WINDOW_NAME
import warlockfe.warlock3.compose.ui.game.OpenGameWindow
import warlockfe.warlock3.compose.ui.game.rememberGameDockState
import warlockfe.warlock3.compose.ui.window.LocalProgressBarSettings
import warlockfe.warlock3.compose.ui.window.LocalWindowFindController
import warlockfe.warlock3.compose.ui.window.ProgressBarSettingsState
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.compose.util.LocalDefaultFont
import warlockfe.warlock3.compose.util.LocalMonoFont
import warlockfe.warlock3.compose.util.LocalPanelFont
import warlockfe.warlock3.compose.util.LocalPanelScale
import warlockfe.warlock3.compose.util.LocalStyleMap
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.isSpecified
import warlockfe.warlock3.core.text.toFontConfig
import warlockfe.warlock3.core.text.toStyleDefinition
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowType

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopGameView(
    viewModel: GameViewModel,
    sideBarVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val entryFocusRequester = remember { FocusRequester() }
    var ignoreNextUnknownKeyEvent by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(gameChrome.appBackground)
                .onPreviewKeyEvent { event ->
                    if (ignoreNextUnknownKeyEvent && event.type == KeyEventType.Unknown) {
                        ignoreNextUnknownKeyEvent = false
                        return@onPreviewKeyEvent true
                    }
                    ignoreNextUnknownKeyEvent = false
                    // The find bar handles its own keys while it's focused; step aside so it can.
                    if (viewModel.windowFindController.focused.value) {
                        return@onPreviewKeyEvent false
                    }
                    viewModel.handleKeyPress(event).also {
                        ignoreNextUnknownKeyEvent = it
                        if (!it &&
                            event.type == KeyEventType.KeyDown &&
                            !event.isAltPressed &&
                            !event.isCtrlPressed &&
                            !event.isMetaPressed
                        ) {
                            entryFocusRequester.requestFocus()
                        }
                    }
                },
    ) {
        val presets by viewModel.presets.collectAsState(emptyMap())
        val baseStyle by viewModel.baseStyle.collectAsState()
        val defaultStyle = baseStyle.toStyleDefinition()
        val monoFont by viewModel.monoFont.collectAsState()
        val panelFont by viewModel.panelFont.collectAsState()
        val panelScale by viewModel.panelScale.collectAsState()
        val openWindows by viewModel.openWindows.collectAsState(emptyList())
        val progressBarSettings by viewModel.progressBarSettings.collectAsState()

        CompositionLocalProvider(
            LocalProgressBarSettings provides
                ProgressBarSettingsState(
                    settings = progressBarSettings,
                    saveColors = viewModel::saveProgressBarColors,
                    saveFont = viewModel::saveProgressBarFont,
                ),
            LocalWindowFindController provides viewModel.windowFindController,
            LocalStyleMap provides presets,
            LocalBaseStyle provides defaultStyle,
            LocalDefaultFont provides baseStyle.toFontConfig(),
            LocalMonoFont provides monoFont,
            LocalPanelFont provides panelFont,
            LocalPanelScale provides panelScale,
        ) {
            Row(modifier = Modifier.weight(1f)) {
                if (sideBarVisible) {
                    val windows by viewModel.residentWindows.collectAsState()
                    val scope = rememberCoroutineScope()
                    val sidebarBackground =
                        defaultStyle.backgroundColor.takeIf { it.isSpecified() }?.toColor()
                            ?: gameChrome.panelAlt
                    val sidebarTextColor =
                        defaultStyle.textColor.takeIf { it.isSpecified() }?.toColor()
                            ?: gameChrome.textPrimary
                    WarlockScrollableColumn(
                        modifier =
                            Modifier
                                .padding(2.dp)
                                .fillMaxHeight()
                                .width(240.dp)
                                .background(
                                    color = sidebarBackground,
                                    shape = RoundedCornerShape(2.dp),
                                ).border(
                                    width = Dp.Hairline,
                                    color = gameChrome.border,
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Streams and panels are different kinds of window (a scrolling text log vs a
                        // fixed layout of widgets), so each gets its own collapsible category instead
                        // of one mixed alphabetical list. A category with no windows is not rendered.
                        val streams = remember(windows) { windows.ofType(WindowType.STREAM) }
                        val panels = remember(windows) { windows.ofType(WindowType.PANEL) }
                        var streamsExpanded by remember { mutableStateOf(true) }
                        var panelsExpanded by remember { mutableStateOf(true) }
                        val item: @Composable (WindowInfo) -> Unit = { window ->
                            // The main text window is the layout's fixed centerpiece: always
                            // listed as shown, never toggleable.
                            val isMain = window.name == MAIN_WINDOW_NAME
                            DesktopWindowListItem(
                                color = sidebarTextColor,
                                windowInfo = window,
                                isOpen = isMain || openWindows.contains(window.name),
                                onClick =
                                    if (isMain) {
                                        null
                                    } else {
                                        { open ->
                                            scope.launch {
                                                if (open) {
                                                    viewModel.openWindow(window.name)
                                                } else {
                                                    viewModel.closeWindow(window.name)
                                                }
                                            }
                                        }
                                    },
                                // A panel is a fixed layout of widgets with no text stream behind it,
                                // so there is nothing for "Clear window" to clear.
                                onClear =
                                    if (window.windowType == WindowType.STREAM) {
                                        { viewModel.clearStream(window.name) }
                                    } else {
                                        null
                                    },
                            )
                        }
                        if (streams.isNotEmpty()) {
                            DesktopWindowCategoryHeader(
                                color = sidebarTextColor,
                                label = "Streams",
                                count = streams.size,
                                expanded = streamsExpanded,
                                onClick = { streamsExpanded = !streamsExpanded },
                            )
                            if (streamsExpanded) streams.forEach { item(it) }
                        }
                        if (panels.isNotEmpty()) {
                            DesktopWindowCategoryHeader(
                                color = sidebarTextColor,
                                label = "Panels",
                                count = panels.size,
                                expanded = panelsExpanded,
                                onClick = { panelsExpanded = !panelsExpanded },
                            )
                            if (panelsExpanded) panels.forEach { item(it) }
                        }
                    }
                }
                // Both lambdas read everything they need from the view model at invocation time and
                // capture only it, so their instances survive recomposition and a keystroke in the
                // entry (which recomposes this view) does not invalidate every docked window.
                val trailingActions =
                    remember(viewModel) {
                        @Composable { state: DockState, window: OpenGameWindow ->
                            DesktopDockWindowActions(state = state, viewModel = viewModel, window = window)
                        }
                    }
                val windowContent =
                    remember(viewModel) {
                        @Composable { window: OpenGameWindow ->
                            DesktopDockedWindow(viewModel = viewModel, window = window)
                        }
                    }
                val dockState =
                    rememberGameDockState(
                        viewModel = viewModel,
                        trailingActions = trailingActions,
                        windowContent = windowContent,
                    )
                Box(Modifier.weight(1f).padding(2.dp)) {
                    JewelDocking {
                        DockArea(dockState, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            DesktopGameBottomBar(viewModel, entryFocusRequester)
        }
    }
    val macroError by viewModel.macroError.collectAsState()
    if (macroError != null) {
        WarlockAlertDialog(
            title = "Macro error",
            text = macroError!!,
            onDismissRequest = { viewModel.handledMacroError() },
            confirmButton = {
                WarlockButton(onClick = { viewModel.handledMacroError() }, text = "OK")
            },
        )
    }
}

/**
 * The sidebar's windows of one kind, in the alphabetical order the flat list used to show them in.
 * A window the game put in the client's own chrome is drawn there, not shown as a window, so it is
 * not offered here either - a toggle for it would have nothing to show.
 */
private fun List<WindowInfo>.ofType(type: WindowType): List<WindowInfo> =
    filter { it.windowType == type && !it.location.isChrome }.sortedBy { it.title }

/**
 * A collapsible category header in the window-list sidebar. Uses the same disclosure-triangle idiom
 * as the settings window list: a rotating [Res.drawable.arrow_right], a dimmed label, and a count.
 *
 * [color] is the sidebar's text color, which follows the character's skin when it sets one. The
 * header dims it rather than reaching for a chrome color, so it cannot end up light-on-light when a
 * light skin background meets the dark app theme.
 */
@Composable
private fun DesktopWindowCategoryHeader(
    color: Color,
    label: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (expanded) "Collapse" else "Expand",
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The arrow is decorative: the row carries the label, the open/closed state, and the action.
        // Merging is what pulls the label onto the button node; neither clickable nor a bare
        // semantics block merges, which would leave a button with a state but no name.
        Image(
            modifier = Modifier.size(12.dp).rotate(if (expanded) 90f else 0f),
            painter = painterResource(Res.drawable.arrow_right),
            colorFilter = ColorFilter.tint(color.copy(alpha = 0.5f)),
            contentDescription = null,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$label ($count)",
            color = color.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun DesktopWindowListItem(
    color: Color,
    windowInfo: WindowInfo,
    isOpen: Boolean,
    // Null makes the row informational: no click toggle and no Hide/Show menu item (the main
    // window, which is always shown).
    onClick: ((Boolean) -> Unit)?,
    onClear: (() -> Unit)?,
) {
    // Per the design: a leading status dot (filled accent when shown, hollow + dimmed when hidden)
    // replaces the old eye icons, and a trailing "..." opens the per-window menu. Clicking the row
    // still toggles the window open/closed.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = { onClick(!isOpen) })
                    } else {
                        Modifier
                    },
                ).padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(12.dp),
            painter = painterResource(if (isOpen) Res.drawable.circle_filled else Res.drawable.circle),
            colorFilter =
                ColorFilter.tint(
                    if (isOpen) gameChrome.accentSubtle else gameChrome.textFaint,
                ),
            contentDescription = if (isOpen) "Shown" else "Hidden",
        )
        Spacer(Modifier.width(10.dp))
        Text(
            modifier = Modifier.weight(1f),
            text = windowInfo.title,
            color = if (isOpen) color else gameChrome.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null || onClear != null) {
            WindowMenuButton(
                tint = gameChrome.textFaint,
                horizontalAlignment = Alignment.End,
            ) { dismiss ->
                if (onClick != null) {
                    selectableItem(
                        selected = false,
                        onClick = {
                            dismiss()
                            onClick(!isOpen)
                        },
                    ) {
                        Text(if (isOpen) "Hide window" else "Show window")
                    }
                }
                if (onClear != null) {
                    selectableItem(
                        selected = false,
                        onClick = {
                            dismiss()
                            onClear()
                        },
                    ) {
                        Text("Clear window")
                    }
                }
            }
        }
    }
}
