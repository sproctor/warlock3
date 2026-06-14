package warlockfe.warlock3.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import warlockfe.warlock3.compose.WarlockApp
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.SkinLoader
import warlockfe.warlock3.core.sge.SgeSettings
import java.io.File

class MainActivity : ComponentActivity() {
    private val logger = Logger.withTag("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        Logger.setLogWriters(platformLogWriter())

        FileKit.init(this)

        val warlockApplication = application as WarlockApplication
        val appContainer = warlockApplication.appContainer

        appContainer.clientSettings
            .observeSkinFile()
            .onEach { skinFile ->
                val bytes =
                    skinFile
                        ?.let { File(it) }
                        ?.takeIf { it.exists() }
                        ?.readBytes()
                        ?: Res.readBytes("files/skin.zip")
                try {
                    appContainer.skin.value = SkinLoader.parse(bytes)
                } catch (e: Exception) {
                    // TODO: notify user of error
                    logger.e(e) { "Failed to load skin file" }
                }
            }.launchIn(appContainer.externalScope)

        val simuCert = runBlocking { Res.readBytes("files/simu.pem") }

        setContent {
            val skin by appContainer.skin.collectAsState()
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
