package cc.warlock.warlock3.app.ui.components

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.core.client.Percentage
import cc.warlock.warlock3.core.client.ProgressBarData
import kotlin.math.min


@Composable
fun VitalBars(vitalBars: Map<String, ProgressBarData>) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val maxWidth = maxWidth
        vitalBars.forEach { (_, progressBarData) ->
            if (progressBarData.groupId == "minivitals") {
                val left = maxWidth * progressBarData.left.value / 100
                val width = maxWidth * progressBarData.width.value / 100
                VitalBar(
                    modifier = Modifier.width(width).height(24.dp).absoluteOffset(x = left),
                    progressBarData = progressBarData
                )
            }
        }
    }
}

@Composable
fun VitalBar(modifier: Modifier, progressBarData: ProgressBarData) {
    val colors = when (progressBarData.id) {
        "health" -> ColorGroup(
            text = Color.White,
            bar = Color(0xFF800000),
            background = Color.DarkGray
        )
        "mana" -> ColorGroup(
            text = Color.White,
            bar = Color.Blue,
            background = Color.DarkGray
        )
        "stamina" -> ColorGroup(
            text = Color.Black,
            bar = Color(0xFFD0982F),
            background = Color(0xFFDECCAA)
        )
        "concentration" -> ColorGroup(
            text = Color.White,
            bar = Color.Blue,
            background = Color.Gray
        )
        "spirit" -> ColorGroup(
            text = Color.Black,
            bar = Color.LightGray,
            background = Color.Gray
        )
        else -> ColorGroup(
            text = Color.Black,
            bar = Color.LightGray,
            background = Color.Gray
        )
    }

    BoxWithConstraints(
        modifier = modifier.background(colors.background)
    ) {
        val percent = min(progressBarData.value.value, 100)
        val width = maxWidth * percent / 100
        Box(modifier = Modifier.width(width).height(24.dp).background(colors.bar))
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = progressBarData.text,
            color = colors.text,
            style = MaterialTheme.typography.caption
        )
    }
}

@Preview
@Composable
fun VitalBarsPreview() {
    val vitalBars = mapOf(
        "health" to ProgressBarData(
            id = "health",
            groupId = "minivitals",
            value = Percentage(80),
            text = "health 80%",
            left = Percentage(0),
            width = Percentage(25)
        ),
        "spirit" to ProgressBarData(
            id = "spirit",
            groupId = "minivitals",
            value = Percentage(100),
            text = "spirit 100%",
            left = Percentage(50),
            width = Percentage(25),
        ),
        "stamina" to ProgressBarData(
            id = "stamina",
            groupId = "minivitals",
            value = Percentage(50),
            text = "fatigue 50%",
            left = Percentage(25),
            width = Percentage(25)
        ),
        "concentration" to ProgressBarData(
            id = "concentration",
            groupId = "minivitals",
            value = Percentage(100),
            text = "concentration 100%",
            left = Percentage(75),
            width = Percentage(25)
        )
    )
    VitalBars(vitalBars)
}

private data class ColorGroup(val text: Color, val bar: Color, val background: Color)