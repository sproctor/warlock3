package warlockfe.warlock3.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.AppContainer
import warlockfe.warlock3.compose.MainScreen
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.settings.SettingsContent
import warlockfe.warlock3.compose.ui.settings.SettingsPage
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.compose.ui.window.StreamRegistryImpl
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.WindowRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarlockApp(
    appContainer: AppContainer,
) {
    val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
    AppTheme(
        useDarkTheme = when (themeSetting) {
            ThemeSetting.AUTO -> isSystemInDarkTheme()
            ThemeSetting.LIGHT -> false
            ThemeSetting.DARK -> true
        }
    ) {
        val gameState = GameState(
            windowRepository = WindowRepository(
                windowSettingsDao = appContainer.database.windowSettingsDao(),
                externalScope = CoroutineScope(Dispatchers.IO),
            ),
            streamRegistry = StreamRegistryImpl(Dispatchers.IO)
        )
        var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
        var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        navigate = {
                            scope.launch {
                                drawerState.close()
                            }
                            settingsPage = it
                        },
                        currentPage = settingsPage,
                    )
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Warlock 3", maxLines = 1) },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        drawerState.open()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
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
                                characterSettingsRepository = appContainer.characterSettingsRepository,
                                aliasRepository = appContainer.aliasRepository,
                                scriptDirRepository = appContainer.scriptDirRepository,
                                alterationRepository = appContainer.alterationRepository,
                                characterRepository = appContainer.characterRepository,
                                clientSettingRepository = appContainer.clientSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerContent(
    navigate: (SettingsPage?) -> Unit,
    currentPage: SettingsPage?,
) {
    ScrollableColumn {
        NavigationDrawerItem(
            modifier = Modifier.padding(horizontal = 12.dp),
            label = { Text("Main") },
            onClick = { navigate(null) },
            selected = currentPage == null,
        )
        SettingsPage.entries.forEach { settingsPage ->
            NavigationDrawerItem(
                modifier = Modifier.padding(horizontal = 12.dp),
                label = { Text(settingsPage.title) },
                onClick = { navigate(settingsPage) },
                selected = currentPage == settingsPage,
            )
        }
    }
}
