package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.CompassView
import warlockfe.warlock3.compose.desktop.ui.window.DesktopDialogContent
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.prefs.repositories.defaultStyles

@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopGameBottomBar(
    viewModel: GameViewModel,
    entryFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val presets by viewModel.presets.collectAsState(emptyMap())
    val style = presets["default"] ?: defaultStyles["default"]!!
    val backgroundColor = style.backgroundColor.toColor()
    val textColor = style.textColor.toColor()
    BoxWithConstraints(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DesktopWarlockEntry(
                    viewModel = viewModel,
                    entryFocusRequester = entryFocusRequester,
                )
                val vitalBars by viewModel.vitalBars.objects.collectAsState()
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
            DesktopIndicatorView(
                indicatorSize = (this@BoxWithConstraints.maxWidth / 20).coerceIn(24.dp, 60.dp),
                backgroundColor = backgroundColor,
                defaultColor = textColor,
                indicators = indicators,
            )
            CompassView(
                size = 88.dp,
                state = viewModel.compassState.collectAsState().value,
                onClick = {
                    viewModel.sendCommand(it.abbreviation)
                },
            )
        }
    }
}
