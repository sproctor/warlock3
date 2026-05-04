package warlockfe.warlock3.compose.util

import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

actual fun FileKitDialogSettings.Companion.createPlatformDialogSettings(title: String): FileKitDialogSettings =
    FileKitDialogSettings(title = title)
