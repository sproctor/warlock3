package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.model.SkinImage
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.indicatorDrawable
import warlockfe.warlock3.compose.util.indicatorEntry
import warlockfe.warlock3.compose.util.indicatorIconModifier
import warlockfe.warlock3.compose.util.indicatorReference

@Composable
fun IndicatorView(
    indicatorSize: Dp,
    indicators: Set<String>,
    backgroundColor: Color,
    defaultColor: Color,
    modifier: Modifier = Modifier,
) {
    // Grouping stays in code; each status's image and position come from the skin's "indicator"
    // section, so every active status in a slot is drawn at its own spot (a status with no offset
    // fills the box).
    val groups: List<List<String>> =
        listOf(
            listOf("kneeling", "prone", "sitting", "standing", "poisoned", "diseased"),
            listOf("joined"),
            listOf("bleeding", "dead"),
            listOf("invisible", "hidden", "webbed"),
            listOf("stunned"),
        )
    val skin = LocalSkin.current
    val reference = skin.indicatorReference()

    Row(modifier = modifier.background(backgroundColor)) {
        groups.forEach { group ->
            Box(
                modifier =
                    Modifier
                        .size(indicatorSize)
                        .border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outline),
            ) {
                group.filter { indicators.contains(it) }.forEach { status ->
                    val entry = skin.indicatorEntry(status) ?: return@forEach
                    val drawable = entry.indicatorDrawable() ?: return@forEach
                    val tint = indicatorTint(status, defaultColor)
                    Image(
                        modifier = indicatorIconModifier(entry, indicatorSize, reference),
                        painter = painterResource(drawable),
                        colorFilter = tint?.let { ColorFilter.tint(it) },
                        contentDescription = status,
                    )
                }
            }
        }
    }
}

// Full-color status assets stay untinted; bleeding is red; everything else takes the default color.
private fun indicatorTint(
    status: String,
    defaultColor: Color,
): Color? =
    when (status) {
        "poisoned", "diseased", "hidden" -> null
        "bleeding" -> Color.Red
        else -> defaultColor
    }

@Preview
@Composable
private fun IndicatorPreview() {
    val previewStatuses = listOf("standing", "stunned", "webbed")
    val skin =
        mapOf(
            "indicator" to
                SkinObject(
                    children = previewStatuses.associateWith { SkinObject(image = SkinImage(name = it)) },
                ),
        )
    CompositionLocalProvider(LocalSkin provides skin) {
        IndicatorView(
            indicatorSize = 60.dp,
            indicators = previewStatuses.toSet(),
            backgroundColor = Color.DarkGray,
            defaultColor = Color.LightGray,
        )
    }
}
