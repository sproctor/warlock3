package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.material3.Material3Docking
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.ui.DockArea
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.generated.resources.circle
import warlockfe.warlock3.compose.generated.resources.circle_filled
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.isSpecified
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowType

/**
 * The Large/Extra-large layout: the original drag-and-drop docking (sidebar window list + docked
 * top/bottom/left/right windows + the dense bottom bar). Unchanged behavior.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun LargeGameView(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    openSettings: () -> Unit,
    navigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var windowListVisible by remember { mutableStateOf(false) }
    Column(modifier) {
        val mainWindow = viewModel.mainWindowUiState.collectAsState()
        val defaultStyle = LocalBaseStyle.current
        val openWindows by viewModel.openWindows.collectAsState(emptyList())
        val character by viewModel.character.collectAsState(null)
        val disconnected by viewModel.disconnected.collectAsState()

        LargeGameTopBar(
            title = character?.name ?: "Warlock",
            subtitle =
                listOfNotNull(
                    character?.gameCode?.takeIf { it.isNotBlank() },
                    mainWindow.value.windowInfo.value
                        ?.subtitle
                        ?.takeIf { it.isNotBlank() },
                ).joinToString(separator = " - ").ifBlank { null },
            connected = !disconnected,
            canReconnect = viewModel.canReconnect,
            onReconnect = viewModel::reconnect,
            onMenu = { windowListVisible = !windowListVisible },
            onSettings = openSettings,
            onDashboard = navigateToDashboard,
        )

        Row(modifier = Modifier.weight(1f)) {
            if (windowListVisible) {
                val windows by viewModel.residentWindows.collectAsState()
                val scope = rememberCoroutineScope()
                ScrollableColumn(
                    Modifier
                        .padding(2.dp)
                        .fillMaxHeight()
                        .width(240.dp)
                        .background(
                            color =
                                defaultStyle.backgroundColor.takeIf { it.isSpecified() }?.toColor()
                                    ?: MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.extraSmall,
                        ).border(
                            width = Dp.Hairline,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.extraSmall,
                        ).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Streams and panels are different kinds of window (a scrolling text log vs a
                    // fixed layout of widgets), so each gets its own collapsible category instead of
                    // one mixed alphabetical list. Matches the desktop sidebar.
                    val streams = remember(windows) { windows.ofType(WindowType.STREAM) }
                    val panels = remember(windows) { windows.ofType(WindowType.PANEL) }
                    var streamsExpanded by remember { mutableStateOf(true) }
                    var panelsExpanded by remember { mutableStateOf(true) }
                    val sidebarTextColor =
                        defaultStyle.textColor.takeIf { it.isSpecified() }?.toColor()
                            ?: MaterialTheme.colorScheme.onSurface
                    val item: @Composable (WindowInfo) -> Unit = { window ->
                        // The main text window is the layout's fixed centerpiece: always listed as
                        // shown, never toggleable.
                        val isMain = window.name == MAIN_WINDOW_NAME
                        WindowListItem(
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
                        )
                    }
                    if (streams.isNotEmpty()) {
                        WindowCategoryHeader(
                            color = sidebarTextColor,
                            label = "Streams",
                            count = streams.size,
                            expanded = streamsExpanded,
                            onClick = { streamsExpanded = !streamsExpanded },
                        )
                        if (streamsExpanded) streams.forEach { item(it) }
                    }
                    if (panels.isNotEmpty()) {
                        WindowCategoryHeader(
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
                        MobileDockWindowActions(state = state, viewModel = viewModel, window = window)
                    }
                }
            val windowContent =
                remember(viewModel) {
                    @Composable { window: OpenGameWindow ->
                        MobileDockedWindow(viewModel = viewModel, window = window)
                    }
                }
            val dockState =
                rememberGameDockState(
                    viewModel = viewModel,
                    trailingActions = trailingActions,
                    windowContent = windowContent,
                )
            Box(Modifier.weight(1f)) {
                Material3Docking {
                    DockArea(dockState, modifier = Modifier.fillMaxSize())
                }
            }
        }
        // Settings and the window-list toggle live in the top app bar, so the bottom bar here is
        // just the entry / vitals / hands / indicators / compass.
        GameBottomBar(
            viewModel = viewModel,
            entryFocusRequester = entryFocusRequester,
        )
    }
}

/** The sidebar's windows of one kind, in the alphabetical order the flat list used to show them in. */
private fun List<WindowInfo>.ofType(type: WindowType): List<WindowInfo> = filter { it.windowType == type }.sortedBy { it.title }

/**
 * A collapsible category header in the window-list sidebar: a rotating disclosure triangle, a dimmed
 * label, and a count. The Material twin of the desktop sidebar's header.
 *
 * [color] is the sidebar's text color, which follows the character's skin when it sets one. The
 * header dims it rather than reaching for a theme color, so it cannot end up light-on-light when a
 * light skin background meets a dark app theme.
 */
@Composable
private fun WindowCategoryHeader(
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
        Icon(
            modifier = Modifier.size(12.dp).rotate(if (expanded) 90f else 0f),
            painter = painterResource(Res.drawable.arrow_right),
            tint = color.copy(alpha = 0.5f),
            contentDescription = null,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$label ($count)",
            color = color.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun WindowListItem(
    color: Color,
    windowInfo: WindowInfo,
    isOpen: Boolean,
    // Null makes the row informational: no click toggle (the main window, which is always shown).
    onClick: ((Boolean) -> Unit)?,
) {
    // A filled accent dot when shown, a hollow dimmed ring when hidden (matching the desktop list).
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
                ).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(12.dp),
            painter = painterResource(if (isOpen) Res.drawable.circle_filled else Res.drawable.circle),
            tint = if (isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = if (isOpen) "Shown" else "Hidden",
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = windowInfo.title,
            color = if (isOpen) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
