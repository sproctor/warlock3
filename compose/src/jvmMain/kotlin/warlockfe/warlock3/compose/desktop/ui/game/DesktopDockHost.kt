package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.desktop.FloatingDockWindows
import com.seanproctor.docking.desktop.FloatingWindowModel
import com.seanproctor.docking.desktop.dockIntoMainWindow
import com.seanproctor.docking.jewel.JewelDocking
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.window.windowHost
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.game.GameWindowStyles
import warlockfe.warlock3.compose.util.LocalSkin

/**
 * The game screen's dock state, published back up to the application scope.
 *
 * A detached window is a real OS window, and Nucleus will only open one from `nucleusApplication` -
 * but the dock state that knows which windows are detached is created deep inside the game screen,
 * several composables below the window the game screen draws in. This carries it back up: the game
 * view publishes its state here, and `Main` reads it to host the detached windows.
 *
 * [state] is null whenever nothing is connected, which is also when there is nothing to detach.
 */
@Stable
class DesktopDockHost {
    var state: DockState? by mutableStateOf(null)
        internal set
}

/**
 * The dock host for the OS window the game screen is drawing in.
 *
 * Ambient rather than a parameter because the game screen is reached through the shared
 * `MainScreen` navigation host: threading a desktop-only docking detail down to it would put it in
 * the signature of every screen on every platform. Null when there is no host, which is every
 * caller that is not the desktop app - previews and tests compose the game view happily without one
 * and simply get no detaching.
 */
val LocalDesktopDockHost = staticCompositionLocalOf<DesktopDockHost?> { null }

/**
 * The game windows the user has torn off the dock, each as a real OS window.
 *
 * Must be called from the `nucleusApplication` scope - that is the only place Nucleus opens an OS
 * window from - which also puts it outside every provider the game screen sits under. So the
 * character's presentation and [skin] are supplied here rather than inherited: a window's fonts,
 * colours and highlights should not change the moment it is detached.
 *
 * Each window draws its own title bar in the main window's colours ([DetachedWindowTitleBar])
 * instead of an OS one: where windows can be placed, the docking system drives the move, which is
 * what lets a window dragged by its title bar dock back where it is let go. Nothing is drawn until
 * the game screen has published a dock state and something has actually been detached.
 *
 * Takes no modifier: it emits OS windows, not layout in a parent.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ktlint:compose:modifier-missing-check")
@Composable
fun NucleusApplicationScope.DetachedGameWindows(
    dockHost: DesktopDockHost,
    viewModel: GameViewModel?,
    skin: Map<String, SkinObject>,
) {
    val state = dockHost.state ?: return
    if (viewModel == null) return
    CompositionLocalProvider(LocalSkin provides skin) {
        GameWindowStyles(viewModel) {
            JewelDocking {
                FloatingDockWindows(state) { model ->
                    DecoratedWindow(
                        onCloseRequest = model.onCloseRequest,
                        state = model.state,
                        title = model.title,
                        icon = model.icon,
                        undecorated = true,
                        resizable = true,
                        // Escape cancels a drag of this window whatever has focus in it.
                        onPreviewKeyEvent = state.windowHost::cancelOnEscape,
                    ) {
                        val window = LocalNucleusWindow.current
                        Column(Modifier.fillMaxSize().border(1.dp, LocalTitleBarStyle.current.colors.border)) {
                            DetachedWindowTitleBar(
                                model = model,
                                onMinimize = { window.setMinimized(true) },
                                modifier = Modifier.detachedWindowDragArea(state, model.windowId),
                            )
                            GameDockArea(state, Modifier.fillMaxWidth().weight(1f), model.windowId)
                        }
                    }
                }
                GameDragPreviewWindow(state)
            }
        }
    }
}

/**
 * The title bar of a detached game window, drawn in Compose to match the main window's: the
 * window's title, then buttons to dock it back (when it holds a single game window), minimize it
 * and close it. [modifier] carries the drag that moves the window.
 */
@Composable
private fun DetachedWindowTitleBar(
    model: FloatingWindowModel,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = LocalWindowInfo.current.isWindowFocused
    val style = LocalTitleBarStyle.current
    val colors = style.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(style.metrics.height)
                .background(if (active) colors.background else colors.inactiveBackground)
                .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = model.title,
            color = colors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (model.dockableId != null) {
            TitleBarButton(
                key = AllIconsKeys.General.OpenInToolWindow,
                description = "Dock in main window",
                tint = colors.content,
                onClick = model::dockIntoMainWindow,
            )
        }
        TitleBarButton(
            key = AllIconsKeys.Windows.Minimize,
            description = "Minimize",
            tint = colors.content,
            onClick = onMinimize,
        )
        TitleBarButton(
            key = AllIconsKeys.Windows.Close,
            description = "Close",
            tint = colors.content,
            onClick = model.onCloseRequest,
        )
    }
}

@Composable
private fun TitleBarButton(
    key: IconKey,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Not focusable: a title bar button taking focus would pull it out of the game window's input.
    IconButton(onClick = onClick, modifier = modifier, focusable = false) {
        Icon(key = key, contentDescription = description, tint = tint)
    }
}
