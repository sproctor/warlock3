package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.state.DockState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.ui.window.DesktopWindowView
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.game.OpenGameWindow
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.core.window.WindowType

/**
 * A game window's body inside the dock area. Headerless: the dock header carries the title, the
 * drag handle, and [DesktopDockWindowActions]. Reads its inputs from the view model here (rather
 * than capturing them at the call site) so the content lambda registered with the dock state stays
 * one stable instance.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
internal fun DesktopDockedWindow(
    viewModel: GameViewModel,
    window: OpenGameWindow,
) {
    val defaultStyle = LocalBaseStyle.current
    val menuData by viewModel.menuData.collectAsState()
    val openWindows by viewModel.openWindows.collectAsState(emptyList())
    val selectedWindow by viewModel.selectedWindow.collectAsState()
    val scrollEvents by viewModel.scrollEvents.collectAsState()
    DesktopWindowView(
        modifier = Modifier.fillMaxSize(),
        uiState = window.uiState,
        location = window.location,
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
 * The dock-header buttons for a game window: the "..." menu (settings, clear, maximize) and a close
 * button. The docking library draws no affordances of its own, so these are the only ones a window
 * gets.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
internal fun DesktopDockWindowActions(
    state: DockState,
    viewModel: GameViewModel,
    window: OpenGameWindow,
) {
    val scope = rememberCoroutineScope()
    val id = DockableId(window.name)
    Row(verticalAlignment = Alignment.CenterVertically) {
        WindowMenuButton(
            tint = gameChrome.textMuted,
            horizontalAlignment = Alignment.End,
        ) { dismiss ->
            selectableItem(
                selected = false,
                onClick = {
                    dismiss()
                    viewModel.requestEditWindowSettings(window.name)
                },
            ) {
                Text("Window settings ...")
            }
            // A panel is a fixed layout of widgets with no text stream behind it, so there is
            // nothing for "Clear window" to clear.
            if (window.uiState.windowInfo.value
                    ?.windowType == WindowType.STREAM
            ) {
                selectableItem(
                    selected = false,
                    onClick = {
                        dismiss()
                        viewModel.clearStream(window.name)
                    },
                ) {
                    Text("Clear window")
                }
            }
            selectableItem(
                selected = false,
                onClick = {
                    dismiss()
                    state.toggleMaximize(id)
                },
            ) {
                Text(if (state.isMaximized(id)) "Restore" else "Maximize")
            }
        }
        if (state.registry[id]?.options?.closable == true) {
            Image(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { scope.launch { state.close(id) } }
                        .padding(2.dp),
                painter = painterResource(Res.drawable.close),
                colorFilter = ColorFilter.tint(gameChrome.textMuted),
                contentDescription = "Close window",
            )
        }
    }
}
