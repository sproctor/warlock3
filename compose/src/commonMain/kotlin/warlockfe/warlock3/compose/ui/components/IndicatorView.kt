package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import warlockfe.warlock3.compose.generated.resources.prone
import warlockfe.warlock3.compose.generated.resources.sitting
import warlockfe.warlock3.compose.generated.resources.standing
import warlockfe.warlock3.compose.generated.resources.stunned
import warlockfe.warlock3.compose.generated.resources.webbed
import warlockfe.warlock3.compose.icons.Local_hospital

private val statusKeysList: Array<Map<String, @Composable () -> Unit>> = arrayOf(
    mapOf(
        "kneeling" to {
            Icon(
                painter = painterResource(Res.drawable.kneeling),
                contentDescription = "kneeling",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        "prone" to {
            Icon(
                painter = painterResource(Res.drawable.prone),
                contentDescription = "prone",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        "sitting" to {
            Icon(
                painter = painterResource(Res.drawable.sitting),
                contentDescription = "sitting",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        "standing" to {
            Icon(
                painter = painterResource(Res.drawable.standing),
                contentDescription = "standing",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
    ),
    mapOf(
        "joined" to {
            Icon(
                painter = painterResource(Res.drawable.joined),
                contentDescription = "joined",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    ),
    mapOf(
        "bleeding" to {
            Icon(
                imageVector = Local_hospital,
                contentDescription = "bleeding",
                tint = Color.Red,
            )
        },
        "dead" to {
            Icon(
                painter = painterResource(Res.drawable.death),
                contentDescription = "dead",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
    ),
    mapOf(
        "invisible" to {
            Icon(
                painter = painterResource(Res.drawable.invisible),
                contentDescription = "invisible",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        "hidden" to {
            Icon(
                painter = painterResource(Res.drawable.hidden),
                contentDescription = "hidden",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        "webbed" to {
            Icon(
                painter = painterResource(Res.drawable.webbed),
                contentDescription = "standing",
                tint = Color.LightGray,
            )
        },
    ),
    mapOf(
        "stunned" to {
            Icon(
                painter = painterResource(Res.drawable.stunned),
                contentDescription = "stunned",
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
    ),
)

@Composable
fun IndicatorView(modifier: Modifier, properties: Map<String, String>) {
    Row(modifier = modifier) {
        statusKeysList.forEachIndexed { index, statusKeys ->
            Box(modifier = Modifier.padding(4.dp).aspectRatio(1f).fillMaxHeight()) {
                statusKeys.filter { properties.containsKey(it.key) }.forEach { it.value() }
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
