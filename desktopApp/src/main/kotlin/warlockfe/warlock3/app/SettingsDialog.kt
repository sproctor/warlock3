package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopSettingsDialog
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.compose.ui.settings.SettingsViewModelFactory
import warlockfe.warlock3.compose.ui.settings.WindowSettingsLiveContext
import warlockfe.warlock3.core.client.GameCharacter

@Composable
fun SettingsDialog(
    currentCharacter: GameCharacter?,
    settingsViewModelFactory: SettingsViewModelFactory,
    crashReporting: CrashReporting,
    closeDialog: () -> Unit,
    initialPage: SettingsPage = SettingsPage.General,
    initialWindowTarget: String? = null,
    windowLiveContext: WindowSettingsLiveContext? = null,
) {
    DesktopSettingsDialog(
        currentCharacter = currentCharacter,
        settingsViewModelFactory = settingsViewModelFactory,
        crashReporting = crashReporting,
        closeDialog = closeDialog,
        initialPage = initialPage,
        initialWindowTarget = initialWindowTarget,
        windowLiveContext = windowLiveContext,
    )
}
