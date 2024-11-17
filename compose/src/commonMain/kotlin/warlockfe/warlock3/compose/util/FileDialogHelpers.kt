package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Composable

@Composable
expect fun DirectoryChooserButton(label: String, title: String, saveDirectory: suspend (String) -> Unit)