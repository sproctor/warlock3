package warlockfe.warlock3.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalLogger
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.sge.SgeSettings
import java.io.File

class MainActivity : ComponentActivity() {

    private val logger = Logger.withTag("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Logger.setLogWriters(platformLogWriter())

        installSplashScreen()

        FileKit.init(this)

        val warlockApplication = application as WarlockApplication
        val appContainer = warlockApplication.appContainer

        // TODO: Move skin loading into common code
        val json = Json {
            ignoreUnknownKeys = true
        }

        val skin = mutableStateOf<Map<String, SkinObject>>(emptyMap())

        appContainer.clientSettings
            .observeSkinFile()
            .onEach { skinFile ->
                val bytes = skinFile
                    ?.let { File(it) }
                    ?.takeIf { it.exists() }
                    ?.readBytes()
                    ?: Res.readBytes("files/skin.json")
                try {
                    skin.value = json.decodeFromString<Map<String, SkinObject>>(bytes.decodeToString())
                } catch (e: Exception) {
                    // TODO: notify user of error
                    logger.e(e) { "Failed to load skin file" }
                }
            }
            .launchIn(appContainer.externalScope)

        val simuCert = runBlocking { Res.readBytes("files/simu.pem") }

        setContent {
            CompositionLocalProvider(
                LocalLogger provides logger,
                LocalSkin provides skin.value,
            ) {
                WarlockApp(
                    appContainer = appContainer,
                    sgeSettings = SgeSettings(
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
