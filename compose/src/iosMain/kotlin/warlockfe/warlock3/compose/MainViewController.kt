package warlockfe.warlock3.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.SkinLoader

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val appContainer = remember { IosAppContainerProvider.appContainer }
        val sgeSettings = remember { IosAppContainerProvider.sgeSettings }

        val logger = remember { Logger.withTag("WarlockiOS") }

        remember {
            appContainer.clientSettings
                .observeSkinFile()
                .onEach {
                    val bytes = Res.readBytes("files/skin.zip")
                    try {
                        appContainer.skin.value = SkinLoader.parse(bytes)
                    } catch (e: Exception) {
                        logger.e(e) { "Failed to load skin file" }
                    }
                }.launchIn(appContainer.externalScope)
            true
        }

        val skin by appContainer.skin.collectAsState()
        CompositionLocalProvider(
            LocalSkin provides skin,
        ) {
            WarlockApp(
                appContainer = appContainer,
                sgeSettings = sgeSettings,
            )
        }
    }
