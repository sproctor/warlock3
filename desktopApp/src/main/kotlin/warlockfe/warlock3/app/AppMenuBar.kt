package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar

// A platform-agnostic description of the app menus, defined once (see TitleBarView) and rendered as a
// native menu bar on macOS ([AppMenuBar]) or as custom popup menus elsewhere, so the two renderers
// can't drift. A null [AppMenu.items] entry is a separator.
internal class AppMenu(
    val title: String,
    val items: List<AppMenuItem?>,
)

internal class AppMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun FrameWindowScope.AppMenuBar(menus: List<AppMenu>) {
    MenuBar {
        menus.forEach { menu ->
            Menu(menu.title) {
                menu.items.forEach { item ->
                    if (item == null) {
                        Separator()
                    } else {
                        Item(text = item.label, enabled = item.enabled, onClick = item.onClick)
                    }
                }
            }
        }
    }
}
