package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
fun WarlockEntry(backgroundColor: Color, textColor: Color, viewModel: GameViewModel) {
    val roundTime by viewModel.roundTime.collectAsState()
    val castTime by viewModel.castTime.collectAsState()
    val clipboard = LocalClipboard.current
    WarlockEntryContent(
        backgroundColor = backgroundColor,
        textColor = textColor,
        state = viewModel.entryText,
        onKeyPress = { viewModel.handleKeyPress(it, clipboard) },
        roundTime = roundTime,
        castTime = castTime,
        sendCommand = viewModel::submit,
    )
}

@Composable
fun WarlockEntryContent(
    backgroundColor: Color,
    textColor: Color,
    state: TextFieldState,
    onKeyPress: (KeyEvent) -> Boolean,
    sendCommand: () -> Unit,
    roundTime: Int,
    castTime: Int,
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = backgroundColor,
    ) {
        Box(
            modifier = Modifier.padding(4.dp)
        ) {
            RoundTimeBar(roundTime, castTime)

            val focusRequester = remember { FocusRequester() }
            var skipNextKey by remember { mutableStateOf(false) }
            BasicTextField(
                state = state,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .focusRequester(focusRequester)
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        // skipNextKey only has an effect on desktop
                        // We're catching KEY_DOWN in AWT, KEY_TYPED gets through
                        // When we match a key, skip the next key event if it's not a KEY_DOWN
                        if (skipNextKey && event.type != KeyEventType.KeyDown) {
                            skipNextKey = false
                            return@onPreviewKeyEvent true
                        }
                        // Only skip the immediately following event
                        skipNextKey = false
                        val result = onKeyPress(event)
                        if (result) {
                            skipNextKey = true
                        }
                        result
                    },
                textStyle = TextStyle.Default.copy(fontSize = 16.sp, color = textColor),
                cursorBrush = SolidColor(textColor),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send,
                ),
                onKeyboardAction = {
                    sendCommand()
                }
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                focusRequester.captureFocus()
            }
        }
    }
}

@Composable
fun BoxScope.RoundTimeBar(
    roundTime: Int,
    castTime: Int,
) {
    val rtColor = Color(0xe0, 0x3c, 0x31, 189)
    val stColor = Color(0x31, 0x3c, 0xe0, 189)
    Canvas(Modifier.matchParentSize().padding(horizontal = 2.dp).clipToBounds()) {
        for (i in 0 until min(100, roundTime)) {
            drawRect(
                color = rtColor,
                topLeft = Offset(x = i * 16.dp.toPx(), y = size.height - 3.dp.toPx()),
                size = Size(width = 12.dp.toPx(), height = 3.dp.toPx())
            )
        }
        for (i in 0 until min(100, castTime)) {
            drawRect(
                color = stColor,
                topLeft = Offset(x = i * 16.dp.toPx(), y = 0f),
                size = Size(width = 12.dp.toPx(), height = 3.dp.toPx())
            )
        }
    }
    Row(Modifier.align(Alignment.CenterEnd)) {
        if (castTime > 0) {
            Text(
                text = castTime.toString(),
                color = Color(0x31, 0x3c, 0xe0),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        if (roundTime > 0) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = roundTime.toString(),
                color = Color(0xe0, 0x3c, 0x31),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Preview(widthDp = 800, backgroundColor = 0xFF444444)
@Composable
fun WarlockEntryPreview() {
    WarlockEntryContent(
        state = rememberTextFieldState("test"),
        roundTime = 8,
        castTime = 4,
        onKeyPress = { true },
        backgroundColor = Color.Unspecified,
        textColor = Color.Black,
        sendCommand = {},
    )
}

@Preview(widthDp = 800, heightDp = 50, backgroundColor = 0xFF444444)
@Composable
fun RoundTimeBarPreview() {
    Box(Modifier.fillMaxSize()) {
        RoundTimeBar(8, 0)
    }
}
