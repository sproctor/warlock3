package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.circle
import warlockfe.warlock3.compose.generated.resources.circle_filled
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.core.window.WindowInfo

/**
 * The phone's window list: what the tab strip could hold, and the only way a window becomes a tab.
 *
 * Windows already in the strip are shown selected rather than hidden, so the list reads as the
 * character's whole set at a glance and tapping one is a shortcut to its tab.
 *
 * Composed unconditionally. With `hideOnCollapse` the rail draws nothing at all while collapsed and
 * renders into its own dialog while expanded, so it never costs the caller any layout - and its
 * open and close animations depend on it staying composed across the transition.
 */
@Composable
fun PhoneWindowRail(
    state: WideNavigationRailState,
    windows: List<WindowInfo>,
    tabs: List<String>,
    onAdd: (String) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sorted = remember(windows) { windows.sortedBy { it.title } }
    ModalWideNavigationRail(
        modifier = modifier,
        state = state,
        hideOnCollapse = true,
        header = {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { scope.launch { state.collapse() } }) {
                    Icon(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close window list",
                    )
                }
                Text(text = "Windows", style = MaterialTheme.typography.titleMedium)
            }
        },
    ) {
        sorted.forEach { window ->
            val isTab = window.name in tabs
            WideNavigationRailItem(
                railExpanded = true,
                selected = isTab,
                icon = {
                    Icon(
                        painter =
                            painterResource(
                                if (isTab) Res.drawable.circle_filled else Res.drawable.circle,
                            ),
                        contentDescription = null,
                    )
                },
                label = { Text(text = window.title, maxLines = 1) },
                onClick = {
                    // A no-op when it is already a tab, so this is both "add" and "go to".
                    onAdd(window.name)
                    onSelect(window.name)
                    scope.launch { state.collapse() }
                },
            )
        }
    }
}
