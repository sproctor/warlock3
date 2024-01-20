package warlockfe.warlock3.compose.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.godaddy.android.colorpicker.ClassicColorPicker
import com.godaddy.android.colorpicker.HsvColor
import warlockfe.warlock3.compose.util.toWarlockColor
import warlockfe.warlock3.core.text.WarlockColor

@Composable
fun ColorPickerDialog(
    initialColor: Color?,
    onCloseRequest: () -> Unit,
    onColorSelected: (color: WarlockColor) -> Unit,
) {
    var currentColor by remember {
        mutableStateOf(
            HsvColor.from(
                initialColor ?: Color.Unspecified
            )
        )
    }
    AlertDialog(
        title = { Text("Choose color") },
        onDismissRequest = onCloseRequest,
        confirmButton = {
            TextButton(
                onClick = { onColorSelected(currentColor.toColor().toWarlockColor()) }
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
            ClassicColorPicker(
                color = currentColor,
                onColorChanged = { color ->
                    currentColor = color
                }
            )
        }
    )
}