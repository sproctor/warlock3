package warlockfe.warlock3.app

import androidx.compose.runtime.Composable
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopSettingsDialog
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
    DesktopSettingsDialog(
        currentCharacter = currentCharacter,
        characterRepository = characterRepository,
        variableRepository = variableRepository,
        macroRepository = macroRepository,
        presetRepository = presetRepository,
        highlightRepository = highlightRepository,
        nameRepository = nameRepository,
        alterationRepository = alterationRepository,
        characterSettingsRepository = characterSettingsRepository,
        aliasRepository = aliasRepository,
        scriptDirRepository = scriptDirRepository,
        clientSettingRepository = clientSettingRepository,
        wraythImporter = wraythImporter,
        closeDialog = closeDialog,
    )
}
