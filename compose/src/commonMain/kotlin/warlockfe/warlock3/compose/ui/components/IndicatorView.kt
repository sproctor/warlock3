package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.ImageResource
import dev.icerock.moko.resources.compose.painterResource
import warlockfe.warlock3.compose.resources.MR

private val invisiblePositionKeys = mapOf(
    "kneeling" to MR.images.inviskneeling,
    "prone" to MR.images.invisprone,
    "sitting" to MR.images.invissitting,
    "standing" to MR.images.invisstanding,
)
private val positionKeys = mapOf(
    "kneeling" to MR.images.kneeling,
    "prone" to MR.images.prone,
    "sitting" to MR.images.sitting,
    "standing" to MR.images.standing,
)
private val status1Keys = mapOf("stunned" to MR.images.stunned)
private val status2Keys = mapOf(
    "bleeding" to MR.images.bleeding,
    "dead" to MR.images.dead,
)
private val status3Keys = mapOf(
    "hidden" to MR.images.hidden,
    "webbed" to MR.images.webbed,
)

@Composable
fun IndicatorView(modifier: Modifier, properties: Map<String, String>) {
    Row(modifier = modifier) {
        val positionMap =
            if (properties.containsKey("invisible")) invisiblePositionKeys else positionKeys
        val position = positionMap.entries.firstOrNull { properties.containsKey(it.key) }?.value
        IndicatorImage(
            images = position?.let { listOf(it) } ?: emptyList()
        )
        VerticalDivider()
        IndicatorImage(
            images = status1Keys.filter { properties.containsKey(it.key) }.map { it.value }
        )
        VerticalDivider()
        IndicatorImage(
            images = if (properties.containsKey("joined")) listOf(MR.images.joined) else emptyList()
        )
        VerticalDivider()
        IndicatorImage(
            images = status2Keys.filter { properties.containsKey(it.key) }.map { it.value }
        )
        VerticalDivider()
        IndicatorImage(
            images = status3Keys.filter { properties.containsKey(it.key) }.map { it.value }
        )
    }
}

@Composable
private fun IndicatorImage(images: List<ImageResource>) {
    Box(modifier = Modifier.padding(4.dp).aspectRatio(1f).fillMaxHeight()) {
        images.forEach { image ->
            StatusImage(image = image, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StatusImage(image: ImageResource, modifier: Modifier) {
    Image(painter = painterResource(image), modifier = modifier, contentDescription = null)
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
