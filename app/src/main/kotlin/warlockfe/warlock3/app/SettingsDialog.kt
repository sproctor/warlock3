package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import warlockfe.warlock3.compose.ui.components.DrawerMenuItem
import warlockfe.warlock3.compose.ui.settings.SettingsContent
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.CharacterRepository
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.MacroRepository
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.VariableRepository

@Composable
fun SettingsDialog(
    currentCharacter: GameCharacter?,
    characterRepository: CharacterRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    presetRepository: PresetRepository,
    highlightRepository: HighlightRepository,
    alterationRepository: AlterationRepository,
    characterSettingsRepository: CharacterSettingsRepository,
    aliasRepository: AliasRepository,
    scriptDirRepository: ScriptDirRepository,
    closeDialog: () -> Unit,
) {
    Window(
        title = "Settings",
        onCloseRequest = closeDialog,
        state = rememberWindowState(width = 900.dp, height = 600.dp)
    ) {
        var state: SettingsPage by remember { mutableStateOf(SettingsPage.General) }

        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet {
                    SettingsPage.entries.forEach { settingsPage ->
                        DrawerMenuItem(
                            title = settingsPage.title,
                            onClick = { state = settingsPage },
                            selected = state == settingsPage,
                        )
                    }
                }
            }
        ) {
            Box(Modifier.padding(16.dp)) {
                SettingsContent(
                    page = state,
                    currentCharacter = currentCharacter,
                    variableRepository = variableRepository,
                    macroRepository = macroRepository,
                    presetRepository = presetRepository,
                    highlightRepository = highlightRepository,
                    characterSettingsRepository = characterSettingsRepository,
                    aliasRepository = aliasRepository,
                    scriptDirRepository = scriptDirRepository,
                    alterationRepository = alterationRepository,
                    characterRepository = characterRepository,
                )
            }
        }
    }
}
