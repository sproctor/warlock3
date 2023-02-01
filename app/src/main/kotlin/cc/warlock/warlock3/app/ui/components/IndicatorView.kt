package cc.warlock.warlock3.app.ui.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.dp

private val positionKeys = listOf("kneeling", "prone", "sitting", "standing")
private val status1Keys = listOf("stunned")
private val status2Keys = listOf("bleeding", "dead")
private val status3Keys = listOf("hidden", "webbed")

@Composable
fun IndicatorView(modifier: Modifier, properties: Map<String, String>) {
    val dividerColor = Color.DarkGray
    Row(modifier = modifier) {
        val invis = if (properties.containsKey("invisible")) "invis" else ""
        val position = positionKeys.firstOrNull { properties.containsKey(it) }?.let { listOf(it + invis) } ?: emptyList()
        IndicatorImage(
            names = position
        )
        Divider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = dividerColor)
        IndicatorImage(
            names = status1Keys.filter { properties.containsKey(it) }
        )
        Divider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = dividerColor)
        IndicatorImage(
            names = if (properties.containsKey("joined")) listOf("joined") else emptyList()
        )
        Divider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = dividerColor)
        IndicatorImage(
            names = status2Keys.filter { properties.containsKey(it) }
        )
        Divider(modifier = Modifier.width(1.dp).fillMaxHeight(), color = dividerColor)
        IndicatorImage(
            names = status3Keys.filter { properties.containsKey(it) }
        )
    }
}

@Composable
private fun IndicatorImage(names: List<String>) {
    val density = LocalDensity.current
    val images = remember(names) {
        names.mapNotNull { name ->
            object {}.javaClass.getResourceAsStream("/images/status/$name.png")?.use {
                BitmapPainter(image = loadImageBitmap(it))
            } ?: object {}.javaClass.getResourceAsStream("/images/status/$name.svg")?.use {
                loadSvgPainter(it, density)
            }
        }
    }
    Box(modifier = Modifier.padding(4.dp).aspectRatio(1f).fillMaxHeight()) {
        images.forEach { image ->
            Image(modifier = Modifier.fillMaxSize(), painter = image, contentDescription = null)
        }
    }
}

@Preview
@Composable
private fun IndicatorPreview() {
    IndicatorView(
        modifier = Modifier
            .height(36.dp)
            .padding(2.dp)
            .background(Color(25, 25, 50)),
        properties = mapOf(
            "standing" to "1",
            "stunned" to "1",
            "webbed" to "1",
        )
    )
}