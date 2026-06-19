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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.death
import warlockfe.warlock3.compose.generated.resources.diseased
import warlockfe.warlock3.compose.generated.resources.hidden
import warlockfe.warlock3.compose.generated.resources.invisible
import warlockfe.warlock3.compose.generated.resources.joined
import warlockfe.warlock3.compose.generated.resources.kneeling
import warlockfe.warlock3.compose.generated.resources.local_hospital
import warlockfe.warlock3.compose.generated.resources.poisoned
import warlockfe.warlock3.compose.generated.resources.prone
import warlockfe.warlock3.compose.generated.resources.sitting
import warlockfe.warlock3.compose.generated.resources.standing
import warlockfe.warlock3.compose.generated.resources.stunned
import warlockfe.warlock3.compose.generated.resources.webbed

private enum class ConditionSeverity { Posture, Danger, Warn, Info }

private data class ConditionInfo(
    val label: String,
    val icon: DrawableResource,
    val severity: ConditionSeverity,
)

// Known status indicators -> chip label, icon (reusing the existing indicator drawables) and a
// tonal severity. Iteration order here is the chip display order.
private val conditionInfo: Map<String, ConditionInfo> =
    mapOf(
        "standing" to ConditionInfo("standing", Res.drawable.standing, ConditionSeverity.Posture),
        "kneeling" to ConditionInfo("kneeling", Res.drawable.kneeling, ConditionSeverity.Posture),
        "prone" to ConditionInfo("prone", Res.drawable.prone, ConditionSeverity.Posture),
        "sitting" to ConditionInfo("sitting", Res.drawable.sitting, ConditionSeverity.Posture),
        "joined" to ConditionInfo("joined", Res.drawable.joined, ConditionSeverity.Info),
        "hidden" to ConditionInfo("hidden", Res.drawable.hidden, ConditionSeverity.Info),
        "invisible" to ConditionInfo("invisible", Res.drawable.invisible, ConditionSeverity.Info),
        "webbed" to ConditionInfo("webbed", Res.drawable.webbed, ConditionSeverity.Warn),
        "stunned" to ConditionInfo("stunned", Res.drawable.stunned, ConditionSeverity.Warn),
        "poisoned" to ConditionInfo("poisoned", Res.drawable.poisoned, ConditionSeverity.Danger),
        "diseased" to ConditionInfo("diseased", Res.drawable.diseased, ConditionSeverity.Danger),
        "bleeding" to ConditionInfo("bleeding", Res.drawable.local_hospital, ConditionSeverity.Danger),
        "dead" to ConditionInfo("dead", Res.drawable.death, ConditionSeverity.Danger),
    )

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
    val active = conditionInfo.filterKeys { indicators.contains(it) }.values.toList()
    if (active.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        active.forEach { info -> ConditionChip(info) }
    }
}

@Composable
private fun ConditionChip(info: ConditionInfo) {
    val (container, content) = severityColors(info.severity)
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
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(info.icon),
            contentDescription = null,
            tint = content,
        )
        Text(text = info.label, color = content, style = MaterialTheme.typography.labelMedium)
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
