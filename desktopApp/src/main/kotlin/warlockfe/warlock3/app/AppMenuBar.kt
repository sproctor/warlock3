package warlockfe.warlock3.app

// A platform-agnostic description of the app menus, defined once (see TitleBarView) and rendered as
// popup menus in the title bar. A null [AppMenu.items] entry is a separator.
internal class AppMenu(
    val title: String,
    val items: List<AppMenuItem?>,
)

internal class AppMenuItem(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)
