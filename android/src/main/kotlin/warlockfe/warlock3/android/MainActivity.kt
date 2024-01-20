package warlockfe.warlock3.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import warlockfe.warlock3.compose.model.GameState
import warlockfe.warlock3.compose.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

//    private val logger = Logger.withTag("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        setContent {
            var showSettings by remember { mutableStateOf(false) }

            val warlockApplication = application as WarlockApplication
            val appContainer = warlockApplication.appContainer
            AppTheme {
                val clipboardManager = LocalClipboardManager.current
                val gameState = remember {
                    mutableStateOf<GameState>(GameState.Dashboard)
                }
                val characterId = when (val currentState = gameState.value) {
                    is GameState.ConnectedGameState -> currentState.viewModel.client.characterId.collectAsState().value
                    else -> null
                }
                WarlockApp(
                    appContainer = appContainer,
                    state = gameState,
                    showSettings = showSettings,
                    closeSettings = { showSettings = false },
                )
            }
        }
    }
}
