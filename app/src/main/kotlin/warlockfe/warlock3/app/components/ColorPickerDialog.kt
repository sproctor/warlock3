package warlockfe.warlock3.app.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.DialogWindow
import warlockfe.warlock3.app.util.toWarlockColor
import warlockfe.warlock3.core.text.WarlockColor
import com.godaddy.android.colorpicker.ClassicColorPicker
import com.godaddy.android.colorpicker.HsvColor

@Composable
fun ColorPickerDialog(
    initialColor: Color?,
    state: DialogState = DialogState(size = DpSize(300.dp, height = 400.dp)),
    onCloseRequest: () -> Unit,
    onColorSelected: (color: WarlockColor) -> Unit,
) {
    var currentColor by remember { mutableStateOf(HsvColor.from(initialColor ?: Color.Unspecified)) }
    DialogWindow(
        title = "Choose color",
        state = state,
        onCloseRequest = onCloseRequest,
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            ClassicColorPicker(
                modifier = Modifier.padding(bottom = 8.dp).weight(1f),
                color = currentColor,
                onColorChanged = { color ->
                    currentColor = color
                }
            )
            Button(
                onClick = { onColorSelected(currentColor.toColor().toWarlockColor()) }
            ) {
                Text("OK")
            }
        }
    }
}