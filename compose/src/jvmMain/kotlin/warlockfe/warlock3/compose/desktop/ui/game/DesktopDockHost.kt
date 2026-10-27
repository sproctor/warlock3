package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seanproctor.docking.desktop.FloatingDockWindows
import com.seanproctor.docking.desktop.FloatingWindowModel
import com.seanproctor.docking.desktop.dockIntoMainWindow
import com.seanproctor.docking.jewel.JewelDocking
import com.seanproctor.docking.model.DockableId
import com.seanproctor.docking.spi.DockingRenderer
import com.seanproctor.docking.spi.HeaderModel
import com.seanproctor.docking.spi.LocalDockingRenderer
import com.seanproctor.docking.state.DockState
import com.seanproctor.docking.window.windowHost
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.LocalNucleusWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import dev.nucleusframework.window.utils.linux.LinuxButtonLayout
import dev.nucleusframework.window.utils.linux.LinuxTitleBarButton
import dev.nucleusframework.window.utils.linux.rememberLinuxButtonLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.game.GameWindowStyles
import warlockfe.warlock3.compose.ui.game.openGameWindows
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
                        // A window holding one game window is named after it, so the title bar
                        // stands in for that window's header: two bars with one title would be one
                        // too many. Its actions move up into the title bar with it.
                        val baseRenderer = LocalDockingRenderer.current
                        val renderer =
                            remember(baseRenderer, model.dockableId) {
                                model.dockableId?.let { HeaderlessRenderer(baseRenderer, it) } ?: baseRenderer
                            }
                        Column(Modifier.fillMaxSize().border(1.dp, LocalTitleBarStyle.current.colors.border)) {
                            val uiStates by viewModel.windowUiStates.collectAsState()
                            val mainUiState by viewModel.mainWindowUiState.collectAsState()
                            val gameWindow =
                                model.dockableId?.let { id -> openGameWindows(mainUiState, uiStates)[id.value] }
                            val maximized by window.maximizedFlow.collectAsState()
                            DetachedWindowTitleBar(
                                model = model,
                                drag = rememberDetachedWindowDrag(state, model.windowId, model.title),
                                onMinimize = { window.setMinimized(true) },
                                isMaximized = maximized,
                                onToggleMaximize = { window.setMaximized(!maximized) },
                                windowActions =
                                    gameWindow?.let {
                                        { DesktopDockWindowActions(state, viewModel, it, inTitleBar = true) }
                                    },
                            )
                            CompositionLocalProvider(LocalDockingRenderer provides renderer) {
                                GameDockArea(state, Modifier.fillMaxWidth().weight(1f), model.windowId)
                            }
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
 * window's title and its controls - the [windowActions] of the game window it holds (its window
 * menu), a button to dock it back (when it holds a single game window), and the window buttons.
 * [drag] says how the bar drags the window.
 *
 * The window buttons are the ones the OS would draw, on the side it would draw them
 * ([rememberWindowButtonLayout]); the dock button and the window menu sit just inside them.
 *
 * Where moving the window and docking it are two places ([DetachedWindowDrag.dockGrip], native
 * Wayland), the title is drawn as a chip with a grip glyph, as Nucleus draws a floating satellite's
 * header there: the chip drags into a dock, the rest of the bar moves the window, and the user has
 * to be able to see which is which. The chip is only as wide as the title, and never more than
 * half the bar it shares with the move, so most of the bar is left to grab the window by.
 */
@Composable
private fun DetachedWindowTitleBar(
    model: FloatingWindowModel,
    drag: DetachedWindowDrag,
    onMinimize: () -> Unit,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    windowActions: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val buttonLayout = rememberWindowButtonLayout()
    val active = LocalWindowInfo.current.isWindowFocused
    val style = LocalTitleBarStyle.current
    val colors = style.colors
    val dockGrip = drag.dockGrip
    val dock: (@Composable () -> Unit)? =
        if (model.dockableId != null) {
            {
                TitleBarButton(
                    key = AllIconsKeys.General.OpenInToolWindow,
                    description = "Dock in main window",
                    tint = colors.content,
                    onClick = model::dockIntoMainWindow,
                )
            }
        } else {
            null
        }
    val windowButtons =
        buttonLayout.buttons.map { button ->
            @Composable {
                when (button) {
                    LinuxTitleBarButton.CLOSE -> {
                        TitleBarButton(AllIconsKeys.Windows.Close, "Close", colors.content, model.onCloseRequest)
                    }

                    LinuxTitleBarButton.MINIMIZE -> {
                        TitleBarButton(AllIconsKeys.Windows.Minimize, "Minimize", colors.content, onMinimize)
                    }

                    LinuxTitleBarButton.MAXIMIZE -> {
                        TitleBarButton(
                            key = if (isMaximized) AllIconsKeys.Windows.Restore else AllIconsKeys.Windows.Maximize,
                            description = if (isMaximized) "Restore" else "Maximize",
                            tint = colors.content,
                            onClick = onToggleMaximize,
                        )
                    }
                }
            }
        }
    // Edge first, as the layout lists them: the window buttons, then what is the game's.
    val controls = windowButtons + listOfNotNull(dock, windowActions)
    val onLeft = !buttonLayout.controlsOnRight
    Row(
        modifier =
            modifier
                .then(drag.bar)
                .fillMaxWidth()
                .height(style.metrics.height)
                .background(if (active) colors.background else colors.inactiveBackground)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onLeft) controls.forEach { it() }
        if (dockGrip != null) {
            // The box takes the free space, so the controls reach the bar's edge; the chip in it is
            // only as wide as its title, ellipsizing at half of the box, and the rest of the box is
            // the bar's move.
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                DockGripChip(
                    title = model.title,
                    color = colors.content,
                    modifier = Modifier.widthIn(max = maxWidth / 2).then(dockGrip),
                )
            }
        } else {
            Text(
                text = model.title,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
        }
        if (!onLeft) controls.asReversed().forEach { it() }
    }
}

/**
 * The window buttons a title bar should draw, and on which side, as the OS draws its own: on Linux
 * the desktop's `button-layout` setting, followed live as the user changes it (GNOME; elsewhere
 * Nucleus falls back to close at the right); on macOS the traffic lights' side and order; on Windows
 * the caption buttons'. [LinuxButtonLayout.buttons] lists them edge first.
 */
@Composable
private fun rememberWindowButtonLayout(): LinuxButtonLayout {
    val os = System.getProperty("os.name").orEmpty()
    return when {
        os.startsWith("Linux", ignoreCase = true) -> {
            rememberLinuxButtonLayout()
        }

        os.startsWith("Mac", ignoreCase = true) -> {
            LinuxButtonLayout(
                buttons = listOf(LinuxTitleBarButton.CLOSE, LinuxTitleBarButton.MINIMIZE, LinuxTitleBarButton.MAXIMIZE),
                controlsOnRight = false,
            )
        }

        else -> {
            LinuxButtonLayout.Default
        }
    }
}

/** The part of a title bar that drags the window into a dock: a rounded chip with a grip glyph. */
@Composable
private fun DockGripChip(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier =
            modifier
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .hoverable(interaction)
                .background(color.copy(alpha = if (hovered) 0.22f else 0.14f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 7.dp, height = 13.dp)) {
            val dot = 1.dp.toPx()
            val stepX = size.width - dot * 2
            val stepY = (size.height - dot * 2) / 2
            for (column in 0..1) {
                for (row in 0..2) {
                    drawCircle(color.copy(alpha = 0.55f), radius = dot, center = Offset(dot + column * stepX, dot + row * stepY))
                }
            }
        }
        Text(
            text = title,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * [base], minus the header of [dockableId]: the game window a detached window holds alone, whose
 * title bar already names it and carries its actions. Every other header - the panes of a split,
 * once something is docked beside it - is drawn as usual.
 */
private class HeaderlessRenderer(
    private val base: DockingRenderer,
    private val dockableId: DockableId,
) : DockingRenderer by base {
    @Composable
    override fun DockableHeader(
        model: HeaderModel,
        modifier: Modifier,
    ) {
        if (model.id != dockableId) base.DockableHeader(model, modifier)
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
