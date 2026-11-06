package warlockfe.warlock3.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import co.touchlab.kermit.Logger
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.prefs.SettingsUnreadableException

@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        // Settings that cannot be read stop the app: starting anyway would mean saving empty
        // settings over the real ones. This says why instead.
        val opened = remember { runCatching { IosAppContainerProvider.appContainer } }
        val problem = (opened.exceptionOrNull() as? SettingsUnreadableException)?.problem
        if (problem != null) {
            SettingsUnreadableScreen(problem)
            return@ComposeUIViewController
        }
        val appContainer = opened.getOrThrow()
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
