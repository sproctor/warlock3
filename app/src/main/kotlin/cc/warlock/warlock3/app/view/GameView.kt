package cc.warlock.warlock3.app.view

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.viewmodel.GameViewModel
import cc.warlock.warlock3.core.Percentage
import cc.warlock.warlock3.core.ProgressBarData
import kotlin.math.min

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameView(viewModel: GameViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        MainGameView(viewModel)
        WarlockEntry(viewModel)
        val progressBars by viewModel.progressBars.collectAsState()
        VitalBars(progressBars)
    }
}

@Composable
fun ColumnScope.MainGameView(viewModel: GameViewModel) {
    val lines by viewModel.lines.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()
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
}

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

@Composable
fun VitalBars(progressBars: Map<String, ProgressBarData>) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val maxWidth = maxWidth
        progressBars.forEach { (_, progressBarData) ->
            println("Drawing progress bar: $progressBarData")
            if (progressBarData.groupId == "minivitals") {
                val left = maxWidth * progressBarData.left.value / 100
                println("left: $left")
                val width = maxWidth * progressBarData.width.value / 100
                println("width: $width")
                VitalBar(
                    modifier = Modifier.width(width).height(24.dp).absoluteOffset(x = left),
                    progressBarData = progressBarData
                )
            }
        }
    }
}

@Composable
fun VitalBar(modifier: Modifier, progressBarData: ProgressBarData) {
    val colors = when (progressBarData.id) {
        "health" -> ColorGroup(
            text = Color.White,
            bar = Color(0xFF800000),
            background = Color.DarkGray
        )
        "mana" -> ColorGroup(
            text = Color.White,
            bar = Color.Blue,
            background = Color.DarkGray
        )
        "stamina" -> ColorGroup(
            text = Color.Black,
            bar = Color(0xFFD0982F),
            background = Color(0xFFDECCAA)
        )
        "concentration" -> ColorGroup(
            text = Color.Black,
            bar = Color.Blue,
            background = Color.Gray
        )
        "spirit" -> ColorGroup(
            text = Color.Black,
            bar = Color.LightGray,
            background = Color.Gray
        )
        else -> ColorGroup(
            text = Color.Black,
            bar = Color.LightGray,
            background = Color.Gray
        )
    }

    BoxWithConstraints(
        modifier = modifier.background(colors.background)
    ) {
        val percent = min(progressBarData.value.value, 100)
        val width = maxWidth * percent / 100
        Box(modifier = Modifier.width(width).height(24.dp).background(colors.bar))
        Text(
            modifier = Modifier.align(Alignment.Center),
            text = progressBarData.text,
            color = colors.text,
            style = MaterialTheme.typography.caption
        )
    }
}

@Preview
@Composable
fun VitalBarsPreview() {
    val progressBars = mapOf(
        "health" to ProgressBarData(
            id = "health",
            groupId = "minivitals",
            value = Percentage(80),
            text = "health 80%",
            left = Percentage(0),
            width = Percentage(25)
        ),
        "spirit" to ProgressBarData(
            id = "spirit",
            groupId = "minivitals",
            value = Percentage(100),
            text = "spirit 100%",
            left = Percentage(50),
            width = Percentage(25),
        ),
        "stamina" to ProgressBarData(
            id = "stamina",
            groupId = "minivitals",
            value = Percentage(50),
            text = "fatigue 50%",
            left = Percentage(25),
            width = Percentage(25)
        ),
        "concentration" to ProgressBarData(
            id = "concentration",
            groupId = "minivitals",
            value = Percentage(100),
            text = "concentration 100%",
            left = Percentage(75),
            width = Percentage(25)
        )
    )
    VitalBars(progressBars)
}

private data class ColorGroup(val text: Color, val bar: Color, val background: Color)