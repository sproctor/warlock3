package warlockfe.warlock3.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import co.touchlab.kermit.Logger
import warlockfe.warlock3.compose.util.LocalSkin

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        val appContainer = remember { IosAppContainerProvider.appContainer }
        val sgeSettings = remember { IosAppContainerProvider.sgeSettings }

        val logger = remember { Logger.withTag("WarlockiOS") }

        remember {
            appContainer.observeSkin(logger)
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
