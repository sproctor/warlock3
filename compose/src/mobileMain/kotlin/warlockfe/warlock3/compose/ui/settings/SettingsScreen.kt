package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.core.client.GameCharacter

/**
 * Top-level settings navigator. Shows a list of [SettingsPage] categories; selecting one shows that
 * page's content. The top-bar navigation icon backs out of a page to the list, then closes settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appContainer: AppContainer,
    currentCharacter: GameCharacter?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page: SettingsPage? by remember { mutableStateOf(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(page?.title ?: "Settings") },
                navigationIcon = {
                    IconButton(onClick = { if (page == null) onClose() else page = null }) {
                        Icon(
                            painter =
                                painterResource(
                                    if (page == null) Res.drawable.close else Res.drawable.arrow_right,
                                ),
                            contentDescription = if (page == null) "Close" else "Back",
                            modifier = if (page == null) Modifier else Modifier.rotate(180f),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val selected = page
            if (selected == null) {
                ScrollableColumn {
                    SettingsPage.entries.forEach { entry ->
                        ListItem(
                            modifier = Modifier.clickable { page = entry },
                            headlineContent = { Text(entry.title) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(Res.drawable.arrow_right),
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            } else {
                SettingsContent(
                    page = selected,
                    currentCharacter = currentCharacter,
                    characterSettingsRepository = appContainer.characterSettingsRepository,
                    characterRepository = appContainer.characterRepository,
                    scriptDirRepository = appContainer.scriptDirRepository,
                    variableRepository = appContainer.variableRepository,
                    macroRepository = appContainer.macroRepository,
                    highlightRepository = appContainer.highlightRepository,
                    nameRepository = appContainer.nameRepository,
                    presetRepository = appContainer.presetRepository,
                    aliasRepository = appContainer.aliasRepository,
                    actionRepository = appContainer.actionRepository,
                    alterationRepository = appContainer.alterationRepository,
                    clientSettingRepository = appContainer.clientSettings,
                    accountRepository = appContainer.accountRepository,
                    wraythImporter = appContainer.wraythImporter,
                )
            }
        }
    }
}
