package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

@Composable
fun WarlockEntry(modifier: Modifier, backgroundColor: Color, textColor: Color, viewModel: GameViewModel) {
    val roundTime by viewModel.roundTime.collectAsState()
    val castTime by viewModel.castTime.collectAsState()
    val clipboard = LocalClipboardManager.current
    WarlockEntryContent(
        modifier = modifier,
        backgroundColor = backgroundColor,
        textColor = textColor,
        textField = viewModel.entryText,
        onValueChange = viewModel::updateEntryText,
        onKeyPress = { viewModel.handleKeyPress(it, clipboard) },
        roundTime = roundTime,
        castTime = castTime,
    )
}

@Composable
fun WarlockEntryContent(
    modifier: Modifier,
    backgroundColor: Color,
    textColor: Color,
    textField: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onKeyPress: (KeyEvent) -> Boolean,
    roundTime: Int,
    castTime: Int,
) {
    Surface(
        modifier = modifier,
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
                value = textField,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .focusRequester(focusRequester)
                    .fillMaxSize()
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
                onValueChange = onValueChange,
                maxLines = 1,
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

//@Preview
//@Composable
//fun WarlockEntryPreview() {
//    WarlockEntryContent(
//        modifier = Modifier.fillMaxWidth().padding(2.dp),
//        textField = TextFieldValue("test"),
//        roundTime = 8,
//        castTime = 4,
//        onKeyPress = { true },
//        onValueChange = {},
//        backgroundColor = Color.Unspecified,
//        textColor = Color.Black,
//    )
//}
//
//@Preview
//@Composable
//fun RoundTimeBarPreview() {
//    Box(Modifier.width(800.dp).height(50.dp).background(Color.DarkGray)) {
//        RoundTimeBar(8, 0)
//    }
//}
