package warlockfe.warlock3.compose.ui.game

import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.window.WindowInfo

/**
 * M3 secondary tabs over the [windows] (one per stream); selecting a tab makes that window the
 * single visible stream. Used by the phone layout (all windows) and the tablet secondary pane
 * (non-main windows).
 */
@Composable
fun StreamTabs(
    windows: List<WindowInfo>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (windows.isEmpty()) return
    val selectedIndex = windows.indexOfFirst { it.name == selected }.coerceAtLeast(0)
    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = 8.dp,
    ) {
        windows.forEach { window ->
            Tab(
                selected = window.name == selected,
                onClick = { onSelect(window.name) },
                text = {
                    Text(text = window.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}
