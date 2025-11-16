package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.death
import warlockfe.warlock3.compose.generated.resources.hidden
import warlockfe.warlock3.compose.generated.resources.invisible
import warlockfe.warlock3.compose.generated.resources.joined
import warlockfe.warlock3.compose.generated.resources.kneeling
import warlockfe.warlock3.compose.generated.resources.local_hospital
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
    modifier: Modifier = Modifier,
) {
    val statusKeysList: Array<Map<String, @Composable () -> Unit>> = arrayOf(
        mapOf(
            "kneeling" to {
                Icon(
                    painter = painterResource(Res.drawable.kneeling),
                    contentDescription = "kneeling",
                    tint = defaultColor,
                )
            },
            "prone" to {
                Icon(
                    painter = painterResource(Res.drawable.prone),
                    contentDescription = "prone",
                    tint = defaultColor,
                )
            },
            "sitting" to {
                Icon(
                    painter = painterResource(Res.drawable.sitting),
                    contentDescription = "sitting",
                    tint = defaultColor,
                )
            },
            "standing" to {
                Icon(
                    painter = painterResource(Res.drawable.standing),
                    contentDescription = "standing",
                    tint = defaultColor,
                )
            },
        ),
        mapOf(
            "joined" to {
                Icon(
                    painter = painterResource(Res.drawable.joined),
                    contentDescription = "joined",
                    tint = defaultColor,
                )
            }
        ),
        mapOf(
            "bleeding" to {
                Icon(
                    painter = painterResource(Res.drawable.local_hospital),
                    contentDescription = "bleeding",
                    tint = Color.Red,
                )
            },
            "dead" to {
                Icon(
                    painter = painterResource(Res.drawable.death),
                    contentDescription = "dead",
                    tint = defaultColor,
                )
            },
        ),
        mapOf(
            "invisible" to {
                Icon(
                    painter = painterResource(Res.drawable.invisible),
                    contentDescription = "invisible",
                    tint = defaultColor,
                )
            },
            "hidden" to {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = painterResource(Res.drawable.hidden),
                    contentDescription = "hidden",
                )
            },
            "webbed" to {
                Icon(
                    painter = painterResource(Res.drawable.webbed),
                    contentDescription = "standing",
                    tint = defaultColor,
                )
            },
        ),
        mapOf(
            "stunned" to {
                Icon(
                    painter = painterResource(Res.drawable.stunned),
                    contentDescription = "stunned",
                    tint = defaultColor,
                )
            },
        ),
    )

    Row(modifier = modifier.background(backgroundColor)) {
        statusKeysList.forEachIndexed { index, statusKeys ->
            Box(modifier = Modifier.aspectRatio(1f).fillMaxHeight().padding(4.dp)) {
                statusKeys.filter { indicators.contains(it.key) }.forEach { it.value() }
            }
            if (index != statusKeysList.lastIndex) {
                VerticalDivider()
            }
        }
    }
}

//@Preview
//@Composable
//private fun IndicatorPreview() {
//    IndicatorView(
//        modifier = Modifier
//            .height(36.dp)
//            .padding(2.dp)
//            .background(Color(25, 25, 50)),
//        properties = mapOf(
//            "standing" to "1",
//            "stunned" to "1",
//            "webbed" to "1",
//        )
//    )
//}
