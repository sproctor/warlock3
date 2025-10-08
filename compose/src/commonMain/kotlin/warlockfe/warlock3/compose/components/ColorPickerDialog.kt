package warlockfe.warlock3.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
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
import warlockfe.warlock3.compose.util.parseHexToColorOrNull
import warlockfe.warlock3.compose.util.toWarlockColor
import warlockfe.warlock3.core.text.WarlockColor

@Composable
fun ColorPickerDialog(
    initialColor: Color?,
    onCloseRequest: () -> Unit,
    onColorSelected: (color: WarlockColor) -> Unit,
) {
    val controller = rememberColorPickerController()
    val hexInput = rememberTextFieldState()
    var initialized by remember { mutableStateOf(false) }
    var hexError by remember { mutableStateOf<String?>(null) }


    AlertDialog(
        title = { Text("Choose color") },
        onDismissRequest = onCloseRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = parseHexToColorOrNull(hexInput.text.toString())
                    val chosen = parsed ?: controller.selectedColor.value
                    onColorSelected(chosen.toWarlockColor())
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onCloseRequest) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(8f)) {
                    HsvColorPicker(
                        modifier = Modifier,
                        controller = controller,
                        initialColor = initialColor,
                        onColorChanged = { colorEnvelope ->
                            if (colorEnvelope.fromUser || !initialized) {
                                initialized = true
                                hexInput.setTextAndPlaceCursorAtEnd(colorEnvelope.hexCode)
                                hexError = null
                            }
                        }
                    )
                }
                AlphaSlider(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    controller = controller,
                )
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    controller = controller,
                )
                LaunchedEffect(hexInput) {
                    snapshotFlow { hexInput.text.toString() }
                        .collectLatest { hexCode ->
                            if (hexCode.isBlank()) {
                                hexError = null
                            } else {
                                val parsed = parseHexToColorOrNull(hexCode)
                                hexError = if (parsed == null) "Invalid hex code" else null
                                if (parsed != null) {
                                    controller.selectByColor(color = parsed, fromUser = false)
                                }
                            }
                        }
                }
                TextField(
                    state = hexInput,
                    label = { Text("HEX (RRGGBB or AARRGGBB)") },
                    prefix = { Text("#", style = MaterialTheme.typography.labelMedium) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    isError = hexError != null,
                    supportingText = {
                        hexError?.let { Text(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                AlphaTile(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(MaterialTheme.shapes.medium),
                    controller = controller,
                )
            }
        }
    )
}

@Composable
fun ColorPickerButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(16.dp).background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            )
        }
    }
}
