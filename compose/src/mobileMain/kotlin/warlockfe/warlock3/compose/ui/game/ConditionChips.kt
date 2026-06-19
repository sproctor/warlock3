package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.indicatorDrawable

// Enum order is the chip display order (chips are grouped by severity, calm to dangerous).
private enum class ConditionSeverity { Posture, Info, Warn, Danger }

// Maps a known status indicator to its chip severity (tone); unknown indicators get no chip. The
// chip label is the status name and the icon comes from the skin's "indicator" section.
private fun conditionSeverity(status: String): ConditionSeverity? =
    when (status) {
        "standing", "kneeling", "prone", "sitting" -> ConditionSeverity.Posture
        "joined", "hidden", "invisible" -> ConditionSeverity.Info
        "webbed", "stunned" -> ConditionSeverity.Warn
        "poisoned", "diseased", "bleeding", "dead" -> ConditionSeverity.Danger
        else -> null
    }

/**
 * The active status indicators rendered as M3 tonal chips (icon + label), used by the phone status
 * card in place of the indicator grid. Unknown indicators are ignored.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionChips(
    indicators: Set<String>,
    modifier: Modifier = Modifier,
) {
    val active =
        indicators
            .mapNotNull { status -> conditionSeverity(status)?.let { status to it } }
            .sortedBy { it.second.ordinal }
    if (active.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        active.forEach { (status, severity) -> ConditionChip(status, severity) }
    }
}

@Composable
private fun ConditionChip(
    status: String,
    severity: ConditionSeverity,
) {
    val (container, content) = severityColors(severity)
    val icon = LocalSkin.current.indicatorDrawable(status)
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .height(28.dp)
                .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
            )
        }
        Text(text = status, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun severityColors(severity: ConditionSeverity): Pair<Color, Color> =
    when (severity) {
        ConditionSeverity.Posture -> {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }

        ConditionSeverity.Danger -> {
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        }

        ConditionSeverity.Warn -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }

        ConditionSeverity.Info -> {
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
