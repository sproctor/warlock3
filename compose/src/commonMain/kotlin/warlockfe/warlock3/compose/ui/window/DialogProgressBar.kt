package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.text.WarlockColor

@Composable
fun DialogProgressBar(
    skinObject: SkinObject?,
    data: DialogObject.ProgressBar,
    modifier: Modifier = Modifier,
    barColorOverride: WarlockColor = WarlockColor.Unspecified,
    backgroundColorOverride: WarlockColor = WarlockColor.Unspecified,
    textColorOverride: WarlockColor = WarlockColor.Unspecified,
) {
    val colorGroup = skinObject.getColorGroup()
    val backgroundColor =
        backgroundColorOverride.toColor().takeOrElse { colorGroup.background }.takeOrElse { Color.DarkGray }
    val barColor =
        barColorOverride.toColor().takeOrElse { colorGroup.bar }.takeOrElse { Color.Blue }
    val textColor =
        textColorOverride.toColor().takeOrElse { colorGroup.text }.takeOrElse { Color.White }
    val percent = data.value.value
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        drawRect(
            color = backgroundColor,
            topLeft = Offset(1f, 1f),
            size = Size(height = size.height - 2f, width = size.width - 2f),
        )
        drawRect(
            color = barColor,
            topLeft = Offset(1f, 1f),
            size = Size(width = (size.width - 2f) * percent / 100, height = size.height - 2f),
        )
        data.text?.let { text ->
            val measuredText =
                textMeasurer.measure(
                    text = text,
                    constraints = Constraints(maxWidth = size.width.toInt()),
                    maxLines = 1,
                    style =
                        TextStyle(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                )
            drawText(
                textLayoutResult = measuredText,
                color = textColor,
                topLeft = Offset(x = (size.width - measuredText.size.width) / 2f, y = (size.height - measuredText.size.height) / 2f),
            )
        }
    }
}
