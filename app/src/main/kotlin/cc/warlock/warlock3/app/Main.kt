package cc.warlock.warlock3.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Compose for Desktop",
        state = rememberWindowState(width = 640.dp, height = 480.dp)
    ) {
        //CompositionLocalProvider(LocalAppResources provides rememberAppResources()) {
        WarlockApp(rememberGameState())
        //}
    }
}