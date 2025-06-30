package warlockfe.warlock3.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.DialogObject
import kotlin.math.min

@Composable
fun DialogContent(
    dataObjects: List<DialogObject>,
    modifier: Modifier = Modifier,
) {
    val colors = mutableMapOf<String, ColorGroup>()
    dataObjects.forEach { data ->
        if (data is DialogObject.Skin) {
            data.controls.forEach { id ->
                val colorGroup = when (data.name) {
                    "healthBar" ->
                        ColorGroup(
                            text = Color.White,
                            bar = Color(0xFF800000),
                            background = Color.DarkGray
                        )

                    "manaBar" ->
                        ColorGroup(
                            text = Color.White,
                            bar = Color.Blue,
                            background = Color.DarkGray
                        )

                    "staminaBar" ->
                        ColorGroup(
                            text = Color.Black,
                            bar = Color(0xFFD0982F),
                            background = Color(0xFFDECCAA)
                        )

                    "spiritBar" ->
                        ColorGroup(
                            text = Color.Black,
                            bar = Color.LightGray,
                            background = Color.Gray
                        )

                    else ->
                        ColorGroup(
                            text = Color.White,
                            bar = Color.Blue,
                            background = Color.Gray
                        )
                }
                colors[id] = colorGroup
            }
        }
    }
    BoxWithConstraints(modifier) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight
        ConstraintLayout {
            val refs = dataObjects.associate { it.id to createRef() }
            dataObjects.forEach { data ->
                val leftMargin = data.left?.toDp(maxWidth) ?: 0.dp
                val topMargin = data.top?.toDp(maxWidth) ?: 0.dp
                val width = data.width?.toDp(maxWidth) ?: Dp.Unspecified
                val height = data.height?.toDp(maxHeight) ?: Dp.Unspecified
                val colors = colors[data.id] ?: ColorGroup(
                    text = Color.White,
                    bar = Color.Blue,
                    background = Color.Gray
                )
                DataObjectContent(
                    modifier = Modifier.size(width, height)
                        .constrainAs(refs[data.id]!!) {
                            val topAnchor = data.topAnchor?.let { refs[it]?.bottom } ?: parent.top
                            top.linkTo(topAnchor, topMargin)
                            val leftAnchor = data.leftAnchor?.let { refs[it]?.absoluteRight } ?: parent.absoluteLeft
                            absoluteLeft.linkTo(leftAnchor, leftMargin)
                        },
                    colorGroup = colors,
                    dataObject = data
                )
            }
        }
    }
}

@Composable
private fun DataObjectContent(
    modifier: Modifier,
    colorGroup: ColorGroup,
    dataObject: DialogObject,
) {
    when (dataObject) {
        is DialogObject.ProgressBar -> ProgressBar(modifier, colorGroup, dataObject)
        is DialogObject.Label -> Label(modifier, colorGroup, dataObject.value ?: "")
        else -> {
            // todo
        }
    }
}

private fun DataDistance.toDp(maxWidth: Dp): Dp {
    return when (this) {
        is DataDistance.Percent -> maxWidth * value.value / 100
        is DataDistance.Pixels -> value.dp
    }
}

@Composable
private fun ProgressBar(
    modifier: Modifier,
    colorGroup: ColorGroup,
    progressBarData: DialogObject.ProgressBar,
) {
    BoxWithConstraints(
        modifier = modifier.background(colorGroup.background)
    ) {
        val percent = min(progressBarData.value.value, 100)
        val width = maxWidth * percent / 100
        Box(modifier = Modifier.width(width).fillMaxHeight().background(colorGroup.bar))
        progressBarData.text?.let { text ->
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = text,
                color = colorGroup.text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Label(
    modifier: Modifier,
    colorGroup: ColorGroup,
    text: String,
) {
    Box(modifier = modifier) {
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = text,
            color = colorGroup.text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

//@Preview
//@Composable
//fun VitalBarsPreview() {
//    val vitalBars = mapOf(
//        "health" to ProgressBarData(
//            id = "health",
//            groupId = "minivitals",
//            value = Percentage(80),
//            text = "health 80%",
//            left = Percentage(0),
//            width = Percentage(25)
//        ),
//        "spirit" to ProgressBarData(
//            id = "spirit",
//            groupId = "minivitals",
//            value = Percentage(100),
//            text = "spirit 100%",
//            left = Percentage(50),
//            width = Percentage(25),
//        ),
//        "stamina" to ProgressBarData(
//            id = "stamina",
//            groupId = "minivitals",
//            value = Percentage(50),
//            text = "fatigue 50%",
//            left = Percentage(25),
//            width = Percentage(25)
//        ),
//        "concentration" to ProgressBarData(
//            id = "concentration",
//            groupId = "minivitals",
//            value = Percentage(100),
//            text = "concentration 100%",
//            left = Percentage(75),
//            width = Percentage(25)
//        )
//    )
//    VitalBars(vitalBars)
//}

private data class ColorGroup(val text: Color, val bar: Color, val background: Color)