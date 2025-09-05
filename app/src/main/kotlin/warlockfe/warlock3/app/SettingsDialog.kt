package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import warlockfe.warlock3.compose.ui.settings.SettingsContent
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.wrayth.settings.WraythImporter

@Composable
fun SettingsDialog(
    currentCharacter: GameCharacter?,
    characterRepository: CharacterRepository,
    variableRepository: VariableRepository,
    macroRepository: MacroRepository,
    presetRepository: PresetRepository,
    highlightRepository: HighlightRepositoryImpl,
    nameRepository: NameRepositoryImpl,
    alterationRepository: AlterationRepository,
    characterSettingsRepository: CharacterSettingsRepository,
    aliasRepository: AliasRepository,
    scriptDirRepository: ScriptDirRepository,
    clientSettingRepository: ClientSettingRepository,
    wraythImporter: WraythImporter,
    closeDialog: () -> Unit,
) {
    DialogWindow(
        title = "Settings",
        onCloseRequest = closeDialog,
        state = rememberDialogState(width = 900.dp, height = 600.dp)
    ) {
        var state: SettingsPage by remember { mutableStateOf(SettingsPage.General) }

        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(
                    modifier = Modifier.width(240.dp)
                ) {
                    SettingsPage.entries.forEach { settingsPage ->
                        NavigationDrawerItem(
                            label = { Text(settingsPage.title) },
                            selected = state == settingsPage,
                            onClick = { state = settingsPage }
                        )
                    }
                }
            }
        ) {
            Surface {
                Box(Modifier.padding(16.dp)) {
                    SettingsContent(
                        page = state,
                        currentCharacter = currentCharacter,
                        variableRepository = variableRepository,
                        macroRepository = macroRepository,
                        presetRepository = presetRepository,
                        highlightRepository = highlightRepository,
                        nameRepository = nameRepository,
                        characterSettingsRepository = characterSettingsRepository,
                        aliasRepository = aliasRepository,
                        scriptDirRepository = scriptDirRepository,
                        alterationRepository = alterationRepository,
                        characterRepository = characterRepository,
                        clientSettingRepository = clientSettingRepository,
                        wraythImporter = wraythImporter,
                    )
                }
            }
        }
    }
}
