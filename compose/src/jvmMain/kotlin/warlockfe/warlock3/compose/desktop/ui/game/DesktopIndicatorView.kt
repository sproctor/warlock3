package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
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
fun DesktopIndicatorView(
    indicatorSize: Dp,
    indicators: Set<String>,
    backgroundColor: Color,
    defaultColor: Color,
    modifier: Modifier = Modifier,
) {
    val statusKeysList: Array<Map<String, @Composable (Modifier) -> Unit>> =
        arrayOf(
            mapOf(
                "kneeling" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.kneeling),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "kneeling",
                    )
                },
                "prone" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.prone),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "prone",
                    )
                },
                "sitting" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.sitting),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "sitting",
                    )
                },
                "standing" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.standing),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "standing",
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
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.joined),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "joined",
                    )
                },
            ),
            mapOf(
                "bleeding" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.local_hospital),
                        colorFilter = ColorFilter.tint(Color.Red),
                        contentDescription = "bleeding",
                    )
                },
                "dead" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.death),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "dead",
                    )
                },
            ),
            mapOf(
                "invisible" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.invisible),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "invisible",
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
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.webbed),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "webbed",
                    )
                },
            ),
            mapOf(
                "stunned" to {
                    Image(
                        modifier = it,
                        painter = painterResource(Res.drawable.stunned),
                        colorFilter = ColorFilter.tint(defaultColor),
                        contentDescription = "stunned",
                    )
                },
            ),
        )

    Row(modifier = modifier.background(backgroundColor)) {
        statusKeysList.forEach { statusKeys ->
            Box(
                modifier =
                    Modifier
                        .size(indicatorSize)
                        .border(width = Dp.Hairline, color = JewelTheme.globalColors.borders.normal)
                        .padding(indicatorSize / 7.5f),
                contentAlignment = Alignment.Center,
            ) {
                statusKeys.filter { indicators.contains(it.key) }.forEach { it.value(Modifier.fillMaxSize()) }
            }
        }
    }
}
