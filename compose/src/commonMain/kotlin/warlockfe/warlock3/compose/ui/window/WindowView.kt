package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.client.WarlockMenuData
import warlockfe.warlock3.core.macro.ScrollEvent
import warlockfe.warlock3.core.text.StyleDefinition

/**
 * A game window: its frame, header, and either the text stream or the panel of widgets behind it.
 *
 * Every actual is the same call to [WindowViewScaffold], which holds the structure both clients
 * share; what each supplies are the slots that genuinely differ between toolkits - the surrounding
 * surface, the header chrome, the scrollbar, the action context menu, and panel content.
 *
 * [showHeader] is false when the window sits in a dock area, whose header (title, drag handle,
 * actions) replaces the window's own.
 */
@Composable
expect fun WindowView(
    uiState: WindowUiState,
    canHide: Boolean,
    defaultStyle: StyleDefinition,
    isSelected: Boolean,
    openWindows: List<String>,
    menuData: WarlockMenuData?,
    onActionClick: (WarlockAction) -> Int?,
    onCloseClick: () -> Unit,
    onOpenWindowSettings: () -> Unit,
    onSelect: () -> Unit,
    scrollEvents: List<ScrollEvent>,
    handledScrollEvent: (ScrollEvent) -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    showHeader: Boolean = true,
    clearStream: () -> Unit,
)
