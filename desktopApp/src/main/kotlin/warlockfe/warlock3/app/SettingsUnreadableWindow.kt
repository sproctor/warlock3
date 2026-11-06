package warlockfe.warlock3.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.theme.WarlockDesktopTheme
import warlockfe.warlock3.compose.desktop.theme.WarlockTitleBar
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.app_icon
import warlockfe.warlock3.core.prefs.SettingsProblem
import kotlin.system.exitProcess

/**
 * Show the user why Warlock will not start, then stop.
 *
 * A window rather than only a line on stderr, because someone who launched Warlock from a desktop
 * icon or a `.sal` file association never sees stderr, and this is the one failure where there is
 * nothing else to show them -- DESKTOP-3Y was exactly that, an app that vanished on launch and
 * reported the reason only to a service the user cannot read.
 */
internal fun reportUnreadableSettingsAndExit(
    problem: SettingsProblem,
    logger: Logger,
): Nothing {
    logger.e { problem.message }
    System.err.println(problem.message)
    nucleusApplication(enableSingleInstance = false) {
        WarlockDesktopTheme(isDark = isSystemInDarkTheme()) {
            // A top-level window rather than the WarlockAlertDialog used everywhere else: that one is
            // a dialog, owned by an application window, and here there is no such window.
            DecoratedWindow(
                onCloseRequest = ::exitApplication,
                title = "Warlock cannot start",
                state = rememberWindowState(size = DpSize(520.dp, 280.dp)),
                resizable = false,
                icon = painterResource(Res.drawable.app_icon),
            ) {
                WarlockTitleBar { Text("Warlock cannot start") }
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(JewelTheme.globalColors.panelBackground)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = problem.message,
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    )
                    WarlockButton(
                        onClick = ::exitApplication,
                        text = "Quit",
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
    exitProcess(1)
}
