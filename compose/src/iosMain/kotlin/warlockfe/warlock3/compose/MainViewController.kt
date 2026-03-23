package warlockfe.warlock3.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.menu
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.ui.settings.SettingsContent
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.sge.SgeSettings

fun MainViewController() = ComposeUIViewController {
    val appContainer = remember { IosAppContainerProvider.appContainer }
    val sgeSettings = remember { IosAppContainerProvider.sgeSettings }

    val logger = remember { Logger.withTag("WarlockiOS") }
    val skin = remember { mutableStateOf<Map<String, SkinObject>>(emptyMap()) }

    remember {
        val json = Json { ignoreUnknownKeys = true }
        appContainer.clientSettings
            .observeSkinFile()
            .onEach { skinFile ->
                val bytes = Res.readBytes("files/skin.json")
                try {
                    skin.value = json.decodeFromString<Map<String, SkinObject>>(bytes.decodeToString())
                } catch (e: Exception) {
                    logger.e(e) { "Failed to load skin file" }
                }
            }
            .launchIn(appContainer.externalScope)
        true
    }

    CompositionLocalProvider(
        LocalLogger provides logger,
        LocalSkin provides skin.value,
    ) {
        IosWarlockApp(
            appContainer = appContainer,
            sgeSettings = sgeSettings,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionName")
@androidx.compose.runtime.Composable
private fun IosWarlockApp(
    appContainer: AppContainer,
    sgeSettings: SgeSettings,
) {
    val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
    AppTheme(
        useDarkTheme = when (themeSetting) {
            ThemeSetting.AUTO -> isSystemInDarkTheme()
            ThemeSetting.LIGHT -> false
            ThemeSetting.DARK -> true
        }
    ) {
        val gameState = GameState()
        var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
        var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    ScrollableColumn {
                        NavigationDrawerItem(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            label = { Text("Main") },
                            onClick = {
                                scope.launch { drawerState.close() }
                                settingsPage = null
                            },
                            selected = settingsPage == null,
                        )
                        SettingsPage.entries.forEach { page ->
                            NavigationDrawerItem(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                label = { Text(page.title) },
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    settingsPage = page
                                },
                                selected = settingsPage == page,
                            )
                        }
                    }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Warlock", maxLines = 1) },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.open() }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.menu),
                                    contentDescription = null
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    if (settingsPage == null) {
                        MainScreen(
                            sgeViewModelFactory = appContainer.sgeViewModelFactory,
                            dashboardViewModelFactory = appContainer.dashboardViewModelFactory,
                            gameState = gameState,
                            updateCurrentCharacter = { currentCharacter = it },
                            sgeSettings = sgeSettings,
                            sideBarVisible = false,
                        )
                    } else {
                        Box(Modifier.padding(16.dp)) {
                            SettingsContent(
                                page = settingsPage!!,
                                currentCharacter = currentCharacter,
                                variableRepository = appContainer.variableRepository,
                                macroRepository = appContainer.macroRepository,
                                presetRepository = appContainer.presetRepository,
                                highlightRepository = appContainer.highlightRepository,
                                nameRepository = appContainer.nameRepository,
                                characterSettingsRepository = appContainer.characterSettingsRepository,
                                aliasRepository = appContainer.aliasRepository,
                                scriptDirRepository = appContainer.scriptDirRepository,
                                alterationRepository = appContainer.alterationRepository,
                                characterRepository = appContainer.characterRepository,
                                clientSettingRepository = appContainer.clientSettings,
                                wraythImporter = appContainer.wraythImporter,
                            )
                        }
                    }
                }
            }
        }
    }
}
