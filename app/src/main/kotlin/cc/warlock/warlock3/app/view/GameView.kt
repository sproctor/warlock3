package cc.warlock.warlock3.app.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.app.viewmodel.toColor
import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.StyledStringLeaf
import cc.warlock.warlock3.core.WarlockColor
import cc.warlock.warlock3.core.WarlockStyle

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(viewModel: GameViewModel) {
    val lines by viewModel.lines.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberLazyListState()
        CompositionLocalProvider(LocalTextStyle provides TextStyle(color = viewModel.textColor.collectAsState().value)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(backgroundColor),
                state = scrollState
            ) {
                items(lines) { line ->
                    Text(line)
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
