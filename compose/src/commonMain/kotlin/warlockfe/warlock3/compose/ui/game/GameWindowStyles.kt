package warlockfe.warlock3.compose.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import warlockfe.warlock3.compose.ui.window.LocalProgressBarSettings
import warlockfe.warlock3.compose.ui.window.LocalWindowFindController
import warlockfe.warlock3.compose.ui.window.LocalWindowSelectionController
import warlockfe.warlock3.compose.ui.window.ProgressBarSettingsState
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.compose.util.LocalPanelDefaults
import warlockfe.warlock3.compose.util.LocalStyleMap
import warlockfe.warlock3.compose.util.PanelDefaults

/**
 * The character's presentation - highlight presets, base style, fonts, panel scale, progress bar
 * settings - as the composition locals a game window's body reads.
 *
 * Its own function because more than one root has to provide exactly the same set. Every game
 * screen wraps its windows with it, and on desktop a detached window cannot inherit them: it
 * composes inside its own OS window, opened from the application scope, so nothing the game screen
 * provides is above it. `Main` wraps those, which is what keeps a window's fonts and colours from
 * changing the moment it is torn off.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun GameWindowStyles(
    viewModel: GameViewModel,
    content: @Composable () -> Unit,
) {
    val presets by viewModel.presets.collectAsState(emptyMap())
    val baseStyle by viewModel.baseStyle.collectAsState()
    val panelFont by viewModel.panelFont.collectAsState()
    val panelScale by viewModel.panelScale.collectAsState()
    val panelDefaults = remember(panelFont, panelScale) { PanelDefaults(panelFont, panelScale) }
    val progressBarSettings by viewModel.progressBarSettings.collectAsState()
    CompositionLocalProvider(
        LocalProgressBarSettings provides
            ProgressBarSettingsState(
                settings = progressBarSettings,
                saveColors = viewModel::saveProgressBarColors,
                saveFont = viewModel::saveProgressBarFont,
            ),
        LocalWindowFindController provides viewModel.windowFindController,
        LocalWindowSelectionController provides viewModel.windowSelectionController,
        LocalStyleMap provides presets,
        LocalBaseStyle provides baseStyle,
        LocalPanelDefaults provides panelDefaults,
        content = content,
    )
}
