package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.compose.generated.resources.more_vert
import warlockfe.warlock3.compose.ui.window.WindowView
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.core.window.WindowType

/**
 * A game window's body inside the dock area. Headerless: the dock header carries the title, the
 * drag handle, and [MobileDockWindowActions]. Reads its inputs from the view model here (rather
 * than capturing them at the call site) so the content lambda registered with the dock state stays
 * one stable instance.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
internal fun MobileDockedWindow(
    viewModel: GameViewModel,
    window: OpenGameWindow,
) {
    val defaultStyle = LocalBaseStyle.current
    val menuData by viewModel.menuData.collectAsState()
    val openWindows by viewModel.openWindows.collectAsState(emptyList())
    val selectedWindow by viewModel.selectedWindow.collectAsState()
    val scrollEvents by viewModel.scrollEvents.collectAsState()
    WindowView(
        modifier = Modifier.fillMaxSize(),
        uiState = window.uiState,
        canHide = !window.isMain,
        defaultStyle = defaultStyle,
        isSelected = selectedWindow == window.name,
        openWindows = openWindows,
        menuData = menuData,
        onActionClick = viewModel::onWindowAction,
        onCloseClick = { viewModel.closeWindow(window.name) },
        onOpenWindowSettings = { viewModel.requestEditWindowSettings(window.name) },
        onSelect = { viewModel.selectWindow(window.name) },
        scrollEvents = scrollEvents,
        handledScrollEvent = viewModel::handledScrollEvent,
        showHeader = false,
        clearStream = { viewModel.clearStream(window.name) },
    )
}

/**
 * The dock-header buttons for a game window: the overflow menu (settings, clear, maximize) and a
 * close button. The docking library draws no affordances of its own, so these are the only ones a
 * window gets.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
internal fun MobileDockWindowActions(
    state: DockState,
    viewModel: GameViewModel,
    window: OpenGameWindow,
) {
    val scope = rememberCoroutineScope()
    val id = DockableId(window.name)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            DockIconButton(
                painter = painterResource(Res.drawable.more_vert),
                contentDescription = "Window menu",
                onClick = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Window settings ...") },
                    onClick = {
                        menuOpen = false
                        viewModel.requestEditWindowSettings(window.name)
                    },
                )
                // A panel is a fixed layout of widgets with no text stream behind it, so there is
                // nothing for "Clear window" to clear.
                if (window.uiState.windowInfo.value
                        ?.windowType == WindowType.STREAM
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear window") },
                        onClick = {
                            menuOpen = false
                            viewModel.clearStream(window.name)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (state.isMaximized(id)) "Restore" else "Maximize") },
                    onClick = {
                        menuOpen = false
                        state.toggleMaximize(id)
                    },
                )
            }
        }
        if (state.registry[id]?.options?.closable == true) {
            DockIconButton(
                painter = painterResource(Res.drawable.close),
                contentDescription = "Close window",
                onClick = { scope.launch { state.close(id) } },
            )
        }
    }
}

/**
 * A dock-header icon button. Visually compact (a Material 3 IconButton is far too tall for the
 * header), but the whole [minimumInteractiveComponentSize] target is clickable: the header's fixed
 * height caps it there, so this widens the touch target without growing the header.
 */
@Composable
private fun DockIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}
