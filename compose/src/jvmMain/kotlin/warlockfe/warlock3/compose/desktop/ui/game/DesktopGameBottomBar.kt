package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.desktop.ui.window.DesktopDialogContent
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.game.IndicatorPalette
import warlockfe.warlock3.compose.ui.game.IndicatorView
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopGameBottomBar(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val presets by viewModel.presets.collectAsState(emptyMap())
    val style = presets["default"] ?: SAFE_DEFAULT_STYLE
    Column(
        modifier
            .fillMaxWidth()
            .background(gameChrome.controlBar),
    ) {
        // Hairline separating the control bar from the work area above it.
        Box(Modifier.fillMaxWidth().height(1.dp).background(gameChrome.border))
        // BoxWithConstraints {
        val density = LocalDensity.current
        // The compass fills the height the rest of the control-bar content (the left column)
        // establishes and scales its width to match, instead of forcing a fixed height onto the
        // bar. We measure that column and feed its height to the compass.
        var contentHeight by remember { mutableStateOf(0.dp) }
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val actionBar by viewModel.actionBar.collectAsState()
            if (actionBar.toolbar.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actionBar.toolbar.forEach { action ->
                        DesktopActionButton(
                            action = action,
                            pool = actionBar.actions,
                            onRunLeaf = viewModel::runActionScript,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .onSizeChanged { contentHeight = with(density) { it.height.toDp() } },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DesktopWarlockEntry(
                        viewModel = viewModel,
                        entryFocusRequester = entryFocusRequester,
                    )
                    val vitalBars by viewModel.vitalBars.objects.collectAsState()
                    // Color overrides come from LocalProgressBarColors, provided by DesktopGameView
                    // around both this control bar and the text windows.
                    DesktopDialogContent(
                        dataObjects = vitalBars,
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        executeCommand = {
                            // Cannot execute commands from vitals bar
                        },
                        style = style,
                    )
                    DesktopHandsView(
                        left = viewModel.leftHand.collectAsState(null).value,
                        right = viewModel.rightHand.collectAsState(null).value,
                        spell = viewModel.spellHand.collectAsState(null).value,
                    )
                }
                val indicators by viewModel.indicators.collectAsState(emptySet())
                val chrome = gameChrome
                IndicatorView(
                    indicators = indicators,
                    shape = RoundedCornerShape(4.dp),
                    palette =
                        IndicatorPalette(
                            inactiveBackground = chrome.panelAlt,
                            inactiveBorder = chrome.border,
                            litBackground = chrome.litBackground,
                            litBorder = chrome.litBorder,
                            litIcon = chrome.litIcon,
                            dangerBackground = chrome.dangerBackground,
                            dangerBorder = chrome.dangerBorder,
                            dangerIcon = chrome.dangerIcon,
                            deadIcon = chrome.textPrimary,
                            joinedBackground = chrome.accent.copy(alpha = 0.22f),
                            joinedBorder = chrome.accent,
                            joinedIcon = chrome.accentSubtle,
                        ),
                    modifier = Modifier.height(contentHeight),
                )
                if (contentHeight > 0.dp) {
                    DesktopCompass(
                        height = contentHeight,
                        directions = viewModel.compassState.collectAsState().value,
                        onClick = { direction ->
                            viewModel.sendCommand(direction.value)
                        },
                        style = viewModel.compassStyle.collectAsState().value,
                        onStyleChange = viewModel::setCompassStyle,
                    )
                }
            }
        }
    }
}
