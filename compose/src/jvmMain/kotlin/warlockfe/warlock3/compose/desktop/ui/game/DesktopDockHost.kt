package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import com.seanproctor.docking.desktop.FloatingDockWindows
import com.seanproctor.docking.desktop.registerDockingWindow
import com.seanproctor.docking.jewel.JewelDocking
import com.seanproctor.docking.state.DockState
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.game.GameWindowStyles
import warlockfe.warlock3.compose.util.LocalSkin

/**
 * The game screen's dock state, published back up to the application scope.
 *
 * A detached window is a real OS window, and Compose will only open one from `application` - but
 * the dock state that knows which windows are detached is created deep inside the game screen,
 * several composables below the window the game screen draws in. This carries it back up: the game
 * view publishes its state here, and `Main` reads it to host the detached windows and to register
 * the main window for cross-window drags.
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
 * Connects the OS window this is called from to the docking system.
 *
 * Dragging a window between the main window and a detached one has to convert between the two
 * windows' coordinates and work out which window a drop landed in, and neither is answerable from
 * inside the composition alone - both need the OS window, which only the window's own scope has.
 *
 * Does nothing until the game screen has published a dock state, which is also while there is
 * nothing to drag.
 */
@Composable
fun FrameWindowScope.RegisterGameDockWindow(dockHost: DesktopDockHost) {
    val state = dockHost.state ?: return
    registerDockingWindow(state)
}

/**
 * The game windows the user has torn off the dock, each as a real OS window.
 *
 * Must be called from the `application` scope - that is the only place Compose opens an OS window
 * from - which also puts it outside every provider the game screen sits under. So the character's
 * presentation and [skin] are supplied here rather than inherited: a window's fonts, colours and
 * highlights should not change the moment it is detached.
 *
 * Windows are left with their OS decorations rather than the Jewel chrome the main window wears,
 * which is what gives them free native move, resize and snap. Nothing is drawn until the game
 * screen has published a dock state and something has actually been detached.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun ApplicationScope.DetachedGameWindows(
    dockHost: DesktopDockHost,
    viewModel: GameViewModel?,
    skin: Map<String, SkinObject>,
) {
    val state = dockHost.state ?: return
    if (viewModel == null) return
    CompositionLocalProvider(LocalSkin provides skin) {
        GameWindowStyles(viewModel) {
            JewelDocking {
                FloatingDockWindows(state)
            }
        }
    }
}
