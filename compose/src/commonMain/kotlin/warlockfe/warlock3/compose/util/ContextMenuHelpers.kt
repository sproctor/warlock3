package warlockfe.warlock3.compose.util

import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession

expect fun TextContextMenuBuilderScope.addItem(key: Any, label: String, onClick: TextContextMenuSession.() -> Unit)

data object SettingsContextMenuItemKey

data object ClearContextMenuItemKey

data object CloseContextMenuItemKey
