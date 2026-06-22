package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.CONTRAST_CROSSOVER_LUMINANCE
import warlockfe.warlock3.compose.util.getColorGroup
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.text.WarlockColor

// Dark halo stamped behind light progress-bar labels (the vital bars' white-text-with-outline
// treatment from the design); near-black with the chrome's slight blue tint.
private val labelOutlineColor = Color(0xFF101216)

// Inset on the left and right so the bar does not touch the edges of its slot.
private val barHorizontalPadding = 2.dp

// Corner radius of the track/fill, matching the design's rounded vital bars.
private val barCornerRadius = 4.dp

@Composable
fun DialogProgressBar(
    skinObject: SkinObject?,
    data: DialogObject.ProgressBar,
    style: TextStyle,
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
        // Cap the inset at a quarter of the width so a narrow skin-defined bar still shows.
        val horizontalInset = barHorizontalPadding.toPx().coerceAtMost(size.width / 4f)
        val trackWidth = size.width - horizontalInset * 2f
        val cornerRadius = CornerRadius(barCornerRadius.toPx())
        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(horizontalInset, 0f),
            size = Size(width = trackWidth, height = size.height),
            cornerRadius = cornerRadius,
        )
        val fillWidth = trackWidth * percent / 100f
        if (fillWidth > 0f) {
            drawRoundRect(
                color = barColor,
                topLeft = Offset(horizontalInset, 0f),
                size = Size(width = fillWidth, height = size.height),
                cornerRadius = cornerRadius,
            )
        }
        data.text?.let { text ->
            val measuredText =
                textMeasurer.measure(
                    text = text,
                    constraints = Constraints(maxWidth = size.width.toInt()),
                    maxLines = 1,
                    style = style,
                )
            val topLeft =
                Offset(
                    x = (size.width - measuredText.size.width) / 2f,
                    y = (size.height - measuredText.size.height) / 2f,
                )
            // Compose has no dedicated text-outline API, so draw the glyphs twice at the same
            // position: first stroked in a dark color to form a halo, then filled on top. A light
            // label (the vital bars and any other light-on-fill bar) needs the halo to stay legible
            // over both the fill and the empty track; dark labels read fine unaided and skip it. The
            // routine is theme-independent - only the resolved fill/track colors change between themes.
            if (textColor.luminance() > CONTRAST_CROSSOVER_LUMINANCE) {
                drawText(
                    textLayoutResult = measuredText,
                    color = labelOutlineColor,
                    topLeft = topLeft,
                    drawStyle =
                        Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                )
            }
            drawText(
                textLayoutResult = measuredText,
                color = textColor,
                topLeft = topLeft,
                drawStyle = Fill,
            )
        }
    }
}
