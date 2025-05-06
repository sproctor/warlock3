package warlockfe.warlock3.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import warlockfe.warlock3.compose.util.LocalLogger

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()

        FileKit.init(this)

        val warlockApplication = application as WarlockApplication
        val appContainer = warlockApplication.appContainer
        val logger = KotlinLogging.logger("main")

        setContent {
            CompositionLocalProvider(
                LocalLogger provides logger,
            ) {
                WarlockApp(appContainer = appContainer)
            }
        }
    }
}
