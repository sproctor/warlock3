package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import warlockfe.warlock3.core.window.WindowLocation

/**
 * A window's title bar and its menu. [onClearClick] is null for a window with nothing to clear (a
 * panel holds widgets, not a text stream), and the menu leaves out "Clear window" in that case.
 */
@Composable
expect fun WindowHeader(
    title: @Composable () -> Unit,
    location: WindowLocation,
    isSelected: Boolean,
    onSettingsClick: () -> Unit,
    onClearClick: (() -> Unit)?,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
)
