package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

@Composable
fun IndicatorView(
    indicators: Set<String>,
    backgroundColor: Color,
    defaultColor: Color,
) {
    val statusKeysList: Array<Map<String, @Composable (Modifier) -> Unit>> = arrayOf(
        mapOf(
            "kneeling" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.kneeling),
                    contentDescription = "kneeling",
                    tint = defaultColor,
                )
            },
            "prone" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.prone),
                    contentDescription = "prone",
                    tint = defaultColor,
                )
            },
            "sitting" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.sitting),
                    contentDescription = "sitting",
                    tint = defaultColor,
                )
            },
            "standing" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.standing),
                    contentDescription = "standing",
                    tint = defaultColor,
                )
            },
            "poisoned" to {
                Image(
                    modifier = it,
                    painter = painterResource(Res.drawable.poisoned),
                    contentDescription = "poisoned",
                )
            },
            "diseased" to {
                Image(
                    modifier = it,
                    painter = painterResource(Res.drawable.diseased),
                    contentDescription = "diseased",
                )
            },
        ),
        mapOf(
            "joined" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.joined),
                    contentDescription = "joined",
                    tint = defaultColor,
                )
            }
        ),
        mapOf(
            "bleeding" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.local_hospital),
                    contentDescription = "bleeding",
                    tint = Color.Red,
                )
            },
            "dead" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.death),
                    contentDescription = "dead",
                    tint = defaultColor,
                )
            },
        ),
        mapOf(
            "invisible" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.invisible),
                    contentDescription = "invisible",
                    tint = defaultColor,
                )
            },
            "hidden" to {
                Image(
                    modifier = it,
                    painter = painterResource(Res.drawable.hidden),
                    contentDescription = "hidden",
                )
            },
            "webbed" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.webbed),
                    contentDescription = "standing",
                    tint = defaultColor,
                )
            },
        ),
        mapOf(
            "stunned" to {
                Icon(
                    modifier = it,
                    painter = painterResource(Res.drawable.stunned),
                    contentDescription = "stunned",
                    tint = defaultColor,
                )
            },
        ),
    )

    Row(modifier = Modifier.background(backgroundColor)) {
        statusKeysList.forEach { statusKeys ->
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(width = Dp.Hairline, color = MaterialTheme.colorScheme.outline)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                statusKeys.filter { indicators.contains(it.key) }.forEach { it.value(Modifier.fillMaxSize()) }
            }
        }
    }
}

@Preview
@Composable
private fun IndicatorPreview() {
    IndicatorView(
        indicators = setOf(
            "standing",
            "stunned",
            "webbed",
        ),
        backgroundColor = Color.DarkGray,
        defaultColor = Color.LightGray,
    )
}
