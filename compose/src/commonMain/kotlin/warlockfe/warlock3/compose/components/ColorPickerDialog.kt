package warlockfe.warlock3.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import warlockfe.warlock3.compose.util.toWarlockColor
import warlockfe.warlock3.core.text.WarlockColor

@Composable
fun ColorPickerDialog(
    initialColor: Color?,
    onCloseRequest: () -> Unit,
    onColorSelected: (color: WarlockColor) -> Unit,
) {
    val controller = rememberColorPickerController()
    AlertDialog(
        title = { Text("Choose color") },
        onDismissRequest = onCloseRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onColorSelected(controller.selectedColor.value.toWarlockColor())
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
                var hexCode by remember { mutableStateOf("") }
                Box(modifier = Modifier.weight(8f)) {
                    HsvColorPicker(
                        modifier = Modifier,
                        controller = controller,
                        initialColor = initialColor,
                        onColorChanged = { colorEnvelope ->
                            hexCode = colorEnvelope.hexCode
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
                Column(horizontalAlignment = CenterHorizontally) {
                    Text("#$hexCode")
                    Spacer(Modifier.height(5.dp))
                    AlphaTile(
                        modifier = Modifier.size(60.dp).clip(MaterialTheme.shapes.medium),
                        controller = controller,
                    )
                }
            }
        }
    )
}