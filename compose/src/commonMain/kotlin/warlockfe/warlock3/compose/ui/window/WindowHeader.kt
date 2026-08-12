package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A window's title bar and its menu. [onClearClick] is null for a window with nothing to clear (a
 * panel holds widgets, not a text stream), and the menu leaves out "Clear window" in that case.
 *
 * [canHide] is false for a window that is the layout's fixed centerpiece rather than one of the
 * windows arranged around it, which drops both "Hide window" and the drag handle. The real client
 * does the same, and says so out loud: closing its Story window prints "* Story window cannot be
 * hidden."
 */
@Composable
expect fun WindowHeader(
    title: @Composable () -> Unit,
    canHide: Boolean,
    isSelected: Boolean,
    onSettingsClick: () -> Unit,
    onClearClick: (() -> Unit)?,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
)
