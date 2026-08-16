package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.LocalProgressBarSettings
import warlockfe.warlock3.compose.ui.window.LocalWindowFindController
import warlockfe.warlock3.compose.ui.window.ProgressBarSettingsState
import warlockfe.warlock3.compose.util.LocalBaseStyle
import warlockfe.warlock3.compose.util.LocalDefaultFont
import warlockfe.warlock3.compose.util.LocalMonoFont
import warlockfe.warlock3.compose.util.LocalPanelFont
import warlockfe.warlock3.compose.util.LocalPanelScale
import warlockfe.warlock3.compose.util.LocalStyleMap
import warlockfe.warlock3.core.text.toFontConfig
import warlockfe.warlock3.core.text.toStyleDefinition

/**
 * The character's presentation - highlight presets, base style, fonts, panel scale, progress bar
 * settings - as the composition locals a game window's body reads.
 *
 * Pulled out of [DesktopGameView] because a detached window needs exactly the same ones and cannot
 * inherit them: it composes inside its own OS window, opened from the application scope, so nothing
 * the game screen provides is above it. The game screen wraps its docked windows with this and
 * `Main` wraps the detached ones, which is what keeps a window's fonts and colours from changing
 * the moment it is torn off.
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun GameWindowStyles(
    viewModel: GameViewModel,
    content: @Composable () -> Unit,
) {
    val presets by viewModel.presets.collectAsState(emptyMap())
    val baseStyle by viewModel.baseStyle.collectAsState()
    val monoFont by viewModel.monoFont.collectAsState()
    val panelFont by viewModel.panelFont.collectAsState()
    val panelScale by viewModel.panelScale.collectAsState()
    val progressBarSettings by viewModel.progressBarSettings.collectAsState()
    CompositionLocalProvider(
        LocalProgressBarSettings provides
            ProgressBarSettingsState(
                settings = progressBarSettings,
                saveColors = viewModel::saveProgressBarColors,
                saveFont = viewModel::saveProgressBarFont,
            ),
        LocalWindowFindController provides viewModel.windowFindController,
        LocalStyleMap provides presets,
        LocalBaseStyle provides baseStyle.toStyleDefinition(),
        LocalDefaultFont provides baseStyle.toFontConfig(),
        LocalMonoFont provides monoFont,
        LocalPanelFont provides panelFont,
        LocalPanelScale provides panelScale,
        content = content,
    )
}
