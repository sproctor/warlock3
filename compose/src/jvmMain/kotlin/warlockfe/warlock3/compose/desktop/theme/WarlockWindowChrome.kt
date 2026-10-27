package warlockfe.warlock3.compose.desktop.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.nucleusframework.window.DecoratedDialogScope
import dev.nucleusframework.window.DecoratedDialogState
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.DecoratedWindowState
import dev.nucleusframework.window.DialogTitleBar
import dev.nucleusframework.window.NucleusDecoratedWindowTheme
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.TitleBarScope
import dev.nucleusframework.window.styling.DecoratedWindowColors
import dev.nucleusframework.window.styling.DecoratedWindowMetrics
import dev.nucleusframework.window.styling.DecoratedWindowStyle
import dev.nucleusframework.window.styling.LocalTitleBarStyle
import dev.nucleusframework.window.styling.TitleBarColors
import dev.nucleusframework.window.styling.TitleBarMetrics
import dev.nucleusframework.window.styling.TitleBarStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor

// Nucleus's Tao windows styled from the Jewel theme: the panel background behind the window and its
// title bar, Jewel's border colour around them, and Jewel's content colour for title-bar text.

/** Provides the Nucleus window and title-bar styles that match the current Jewel theme. */
@Composable
internal fun WarlockWindowChrome(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val background = JewelTheme.globalColors.panelBackground
    val contentColor = JewelTheme.contentColor
    val border = JewelTheme.globalColors.borders.normal
    val windowStyle =
        remember(background, border) {
            DecoratedWindowStyle(
                colors =
                    DecoratedWindowColors(
                        border = border,
                        borderInactive = border.copy(alpha = 0.5f),
                        background = background,
                    ),
                metrics = DecoratedWindowMetrics(borderWidth = 1.dp),
            )
        }
    val titleBarStyle =
        remember(background, contentColor, border) {
            TitleBarStyle(
                colors =
                    TitleBarColors(
                        background = background,
                        inactiveBackground = background,
                        content = contentColor,
                        border = border,
                        fullscreenControlButtonsBackground = background,
                    ),
                metrics = TitleBarMetrics(height = 40.dp, titlePaneButtonSize = DpSize(40.dp, 40.dp)),
            )
        }
    NucleusDecoratedWindowTheme(isDark = isDark, windowStyle = windowStyle, titleBarStyle = titleBarStyle, content = content)
}

/** A window's title bar, with Jewel text inside it drawn in the title bar's content colour. */
@Composable
fun DecoratedWindowScope.WarlockTitleBar(
    modifier: Modifier = Modifier,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit = {},
) {
    val style = LocalTitleBarStyle.current
    TitleBar(modifier = modifier, style = style) { state ->
        CompositionLocalProvider(LocalContentColor provides style.colors.content) { content(state) }
    }
}

/** A dialog's title bar, with Jewel text inside it drawn in the title bar's content colour. */
@Composable
fun DecoratedDialogScope.WarlockDialogTitleBar(
    modifier: Modifier = Modifier,
    content: @Composable TitleBarScope.(DecoratedDialogState) -> Unit = {},
) {
    val style = LocalTitleBarStyle.current
    DialogTitleBar(modifier = modifier, style = style) { state ->
        CompositionLocalProvider(LocalContentColor provides style.colors.content) { content(state) }
    }
}
