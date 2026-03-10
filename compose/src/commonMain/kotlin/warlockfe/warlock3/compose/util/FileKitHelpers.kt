package warlockfe.warlock3.compose.util

import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

expect fun FileKitDialogSettings.Companion.createPlatformDialogSettings(
    title: String
): FileKitDialogSettings