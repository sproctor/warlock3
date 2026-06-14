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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.menu
import warlockfe.warlock3.compose.generated.resources.settings_filled
import warlockfe.warlock3.compose.model.GameScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.settings.SettingsScreen
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.sge.SgeSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarlockApp(
    appContainer: AppContainer,
    sgeSettings: SgeSettings,
    modifier: Modifier = Modifier,
) {
    val themeSetting by appContainer.clientSettings.observeTheme().collectAsState(ThemeSetting.AUTO)
    val darkMode =
        when (themeSetting) {
            ThemeSetting.AUTO -> isSystemInDarkTheme()
            ThemeSetting.LIGHT -> false
            ThemeSetting.DARK -> true
        }
    LaunchedEffect(darkMode) { appContainer.darkMode.value = darkMode }
    AppTheme(useDarkTheme = darkMode) {
        val gameState = remember { GameState() }
        var showSettings by remember { mutableStateOf(false) }
        var currentCharacter: GameCharacter? by remember { mutableStateOf(null) }

        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            modifier = modifier,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        openSettings = {
                            scope.launch { drawerState.close() }
                            showSettings = true
                        },
                    )
                }
            },
        ) {
            if (showSettings) {
                SettingsScreen(
                    appContainer = appContainer,
                    currentCharacter = currentCharacter,
                    onClose = { showSettings = false },
                )
            } else {
                Scaffold(
                    topBar = {
                        // The connected game is chromeless; settings are reached from its bottom bar.
                        if (gameState.screen !is GameScreen.ConnectedGameState) {
                            WarlockTopBar(openDrawer = { scope.launch { drawerState.open() } })
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        MainScreen(
                            sgeViewModelFactory = appContainer.sgeViewModelFactory,
                            dashboardViewModelFactory = appContainer.dashboardViewModelFactory,
                            gameState = gameState,
                            updateCurrentCharacter = { currentCharacter = it },
                            sgeSettings = sgeSettings,
                            openSettings = { showSettings = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    openSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableColumn(modifier) {
        NavigationDrawerItem(
            modifier = Modifier.padding(horizontal = 12.dp),
            label = { Text("Settings") },
            icon = {
                Icon(
                    painter = painterResource(Res.drawable.settings_filled),
                    contentDescription = null,
                )
            },
            onClick = openSettings,
            selected = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarlockTopBar(openDrawer: () -> Unit) {
    TopAppBar(
        title = { Text(text = "Warlock", maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = openDrawer) {
                Icon(
                    painter = painterResource(Res.drawable.menu),
                    contentDescription = null,
                )
            }
        },
    )
}
