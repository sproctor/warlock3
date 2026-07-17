package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.ui.window.DialogContent
import warlockfe.warlock3.compose.util.LocalBaseStyle

/**
 * The phone status card: vitals (the game-provided minivitals bars), the hands, and the active
 * condition chips, grouped on a tonal surface. Replaces the dense indicator grid on phones.
 */
@Composable
fun GameStatusCard(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
) {
    val style = LocalBaseStyle.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val vitalBars by viewModel.vitalBars.objects.collectAsState()
            DialogContent(
                dataObjects = vitalBars,
                modifier = Modifier.fillMaxWidth().height(16.dp),
                executeCommand = {
                    // Vitals are display-only.
                },
                style = style,
            )
            HandsView(
                left = viewModel.leftHand.collectAsState(null).value,
                right = viewModel.rightHand.collectAsState(null).value,
                spell = viewModel.spellHand.collectAsState(null).value,
            )
            val indicators by viewModel.indicators.collectAsState(emptySet())
            ConditionChips(indicators = indicators, modifier = Modifier.fillMaxWidth())
        }
    }
}
