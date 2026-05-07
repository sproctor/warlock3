package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
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

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopSettingsDialog(
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
        state = rememberDialogState(width = 900.dp, height = 650.dp),
    ) {
        var page: SettingsPage by remember { mutableStateOf(SettingsPage.General) }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .width(220.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                ) {
                    SettingsPage.entries.forEach { entry ->
                        SettingsNavItem(
                            label = entry.title,
                            selected = page == entry,
                            onClick = { page = entry },
                        )
                    }
                }
                Divider(orientation = Orientation.Vertical, modifier = Modifier.fillMaxHeight())
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                ) {
                    DesktopSettingsContent(
                        page = page,
                        currentCharacter = currentCharacter,
                        characterSettingsRepository = characterSettingsRepository,
                        characterRepository = characterRepository,
                        scriptDirRepository = scriptDirRepository,
                        variableRepository = variableRepository,
                        macroRepository = macroRepository,
                        highlightRepository = highlightRepository,
                        nameRepository = nameRepository,
                        presetRepository = presetRepository,
                        aliasRepository = aliasRepository,
                        alterationRepository = alterationRepository,
                        clientSettingRepository = clientSettingRepository,
                        wraythImporter = wraythImporter,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (selected) {
            JewelTheme.globalColors.borders.normal
                .copy(alpha = 0.25f)
        } else {
            Color.Transparent
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label)
    }
}
