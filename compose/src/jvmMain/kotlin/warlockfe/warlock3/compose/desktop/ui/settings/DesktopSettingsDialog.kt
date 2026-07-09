package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.app_icon
import warlockfe.warlock3.compose.ui.settings.SettingsGroup
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.compose.ui.settings.WindowSettingsLiveContext
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.AccountRepository
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
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
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository

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
    actionRepository: ActionRepository,
    scriptDirRepository: ScriptDirRepository,
    clientSettingRepository: ClientSettingRepository,
    accountRepository: AccountRepository,
    windowSettingRepository: WindowSettingsRepository,
    closeDialog: () -> Unit,
    initialPage: SettingsPage = SettingsPage.General,
    initialWindowTarget: String? = null,
    windowLiveContext: WindowSettingsLiveContext? = null,
) {
    DialogWindow(
        title = "Settings",
        onCloseRequest = closeDialog,
        // Without an explicit icon the title bar falls back to the JBR default (Windows shows it).
        icon = painterResource(Res.drawable.app_icon),
        state = rememberDialogState(width = 900.dp, height = 650.dp),
    ) {
        var page: SettingsPage by remember { mutableStateOf(initialPage) }

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
                            // 180 fits the longest label plus its leading icon
                            .width(180.dp)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                ) {
                    SettingsGroup.entries.forEach { group ->
                        if (group.title.isNotEmpty()) {
                            Text(
                                text = group.title,
                                style = JewelTheme.defaultTextStyle.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
                            )
                        }
                        SettingsPage.entries.filter { it.group == group }.forEach { entry ->
                            SettingsNavItem(
                                label = entry.title,
                                icon = entry.icon,
                                selected = page == entry,
                                onClick = { page = entry },
                            )
                        }
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
                        actionRepository = actionRepository,
                        alterationRepository = alterationRepository,
                        clientSettingRepository = clientSettingRepository,
                        accountRepository = accountRepository,
                        windowSettingRepository = windowSettingRepository,
                        initialWindowTarget = initialWindowTarget,
                        windowLiveContext = windowLiveContext,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavItem(
    label: String,
    icon: DrawableResource,
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(JewelTheme.globalColors.text.normal),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
