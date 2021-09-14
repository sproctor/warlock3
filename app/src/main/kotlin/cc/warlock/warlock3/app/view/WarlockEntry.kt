package cc.warlock.warlock3.app.view

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import cc.warlock.warlock3.app.viewmodel.GameViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WarlockEntry(viewModel: GameViewModel) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val textState = remember { mutableStateOf("") }
        OutlinedTextField(
            value = textState.value,
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    if (event.key.keyCode == Key.Enter.keyCode && event.type == KeyEventType.KeyDown) {
                        viewModel.send(textState.value)
                        textState.value = ""
                        true
                    } else {
                        false
                    }
                },
            onValueChange = { textState.value = it },
            maxLines = 1,
        )
        Button(
            onClick = {
                viewModel.send(textState.value)
                textState.value = ""
            }
        ) {
            Text("SEND")
        }
    }
}