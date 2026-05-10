package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.util.parseHexOrNull
import warlockfe.warlock3.compose.util.toWarlockColor
import warlockfe.warlock3.core.text.WarlockColor

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopColorPickerDialog(
    initialColor: Color?,
    onCloseRequest: () -> Unit,
    onColorSelect: (color: WarlockColor) -> Unit,
) {
    val controller = rememberColorPickerController()
    val hexInput = rememberTextFieldState()
    var initialized by remember { mutableStateOf(false) }
    var hexError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialColor) {
        if (initialColor != null) {
            controller.selectByColor(initialColor, fromUser = false)
        }
    }

    WarlockDialog(
        title = "Choose color",
        onCloseRequest = onCloseRequest,
        width = 480.dp,
        height = 640.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(8f).fillMaxWidth()) {
                HsvColorPicker(
                    modifier = Modifier.fillMaxSize(),
                    controller = controller,
                    onColorChanged = { colorEnvelope ->
                        if (colorEnvelope.fromUser || !initialized) {
                            initialized = true
                            hexInput.setTextAndPlaceCursorAtEnd(colorEnvelope.hexCode)
                            hexError = null
                        }
                    },
                )
            }
            AlphaSlider(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                controller = controller,
            )
            BrightnessSlider(
                modifier = Modifier.fillMaxWidth().height(36.dp),
                controller = controller,
            )
            LaunchedEffect(hexInput) {
                snapshotFlow { hexInput.text.toString() }
                    .collectLatest { hexCode ->
                        if (hexCode.isBlank()) {
                            hexError = null
                        } else {
                            val parsed = Color.parseHexOrNull(hexCode)
                            hexError = if (parsed == null) "Invalid hex code" else null
                            if (parsed != null) {
                                controller.selectByColor(color = parsed, fromUser = false)
                            }
                        }
                    }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("#")
                WarlockTextField(
                    state = hexInput,
                    modifier = Modifier.weight(1f),
                    placeholder = "RRGGBB or AARRGGBB",
                )
                AlphaTile(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp)),
                    controller = controller,
                )
            }
            hexError?.let { Text(it) }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onCloseRequest, text = "Cancel")
                WarlockButton(
                    onClick = {
                        val parsed = Color.parseHexOrNull(hexInput.text.toString())
                        val chosen = parsed ?: controller.selectedColor.value
                        onColorSelect(chosen.toWarlockColor())
                    },
                    text = "OK",
                )
            }
        }
    }
}

@Composable
fun DesktopColorPickerButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WarlockOutlinedButton(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(16.dp)
                    .background(color)
                    .border(1.dp, JewelTheme.globalColors.borders.normal),
            )
        }
    }
}
