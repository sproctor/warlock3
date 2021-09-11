package cc.warlock.warlock3.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import cc.warlock.warlock3.app.viewmodel.GameViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(viewModel: GameViewModel) {
    val lines by viewModel.lines.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberLazyListState()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(backgroundColor),
        ) {
            val height = this.maxHeight
            SelectionContainer {
                val textColor by viewModel.textColor.collectAsState()
                CompositionLocalProvider(LocalTextStyle provides TextStyle(color = textColor)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height),
                        state = scrollState
                    ) {
                        items(lines) { line ->
                            Text(line)
                        }
                    }
                }
            }
        }
        LaunchedEffect(lines) {
            scrollState.scrollToItem(lines.lastIndex)
        }
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
}
