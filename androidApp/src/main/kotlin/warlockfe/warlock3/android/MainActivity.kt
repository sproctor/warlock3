package warlockfe.warlock3.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.compose.WarlockApp
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.observeSkin
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.sge.SgeSettings
import java.io.File

class MainActivity : ComponentActivity() {
    private val logger = Logger.withTag("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        Logger.setLogWriters(platformLogWriter())

        FileKit.init(this)

        val warlockApplication = application as WarlockApplication
        val appContainer = warlockApplication.appContainer

        appContainer.observeSkin(logger) { path ->
            File(path).takeIf { it.exists() }?.readBytes()
        }

        val simuCert = runBlocking { Res.readBytes("files/simu.pem") }

        setContent {
            val skin by appContainer.skin.collectAsState()
            // The system bars are transparent and drawn over our content, so the app owns whether
            // their icons are light or dark. enableEdgeToEdge picks that from the system's dark
            // mode, which is wrong whenever the user's own theme setting disagrees with it - a
            // light app under a dark system left white icons on a white bar. Follow the resolved
            // app theme instead; WarlockApp keeps [AppContainer.darkMode] in step with it.
            val darkMode by appContainer.darkMode.collectAsState()
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkMode
                    isAppearanceLightNavigationBars = !darkMode
                }
            }
            CompositionLocalProvider(
                LocalSkin provides skin,
            ) {
                WarlockApp(
                    appContainer = appContainer,
                    sgeSettings =
                        SgeSettings(
                            host = "eaccess.play.net",
                            port = 7910,
                            certificate = simuCert,
                            secure = true,
                        ),
                )
            }
        }
    }
}
