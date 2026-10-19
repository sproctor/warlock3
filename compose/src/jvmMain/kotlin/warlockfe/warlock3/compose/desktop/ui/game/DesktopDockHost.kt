package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.seanproctor.docking.state.DockState

/**
 * The game screen's dock state, published back up to the application scope.
 *
 * The dock state is created deep inside the game screen, several composables below the window the
 * game screen draws in; the game view publishes it here so the app's window can reach it. On the
 * Tao window backend game windows never float (see [hostSupportsFloatingWindows]), so nothing is
 * hosted outside the window yet.
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
