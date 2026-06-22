package warlockfe.warlock3.compose.ui.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.compose.generated.resources.keyboard_double_arrow_down
import warlockfe.warlock3.compose.generated.resources.keyboard_double_arrow_up

// Self-contained colors so the bar reads consistently over any (typically dark) window background.
private val barBackground = Color(0xF02B2B2B)
private val barContent = Color(0xFFEDEDED)
private val barBorder = Color(0x33FFFFFF)

/**
 * Floating find-in-window bar overlaid on the window. It handles its own keys, but only while it is
 * focused (Enter / Ctrl+F = next, Ctrl+Shift+F = previous, Escape = close); [onFocusChanged] reports
 * that focus so the game view's global key handler can step aside while the bar has focus and resume
 * when focus moves elsewhere (e.g. the user clicks back into the command entry). The query field
 * auto-focuses on open.
 */
@Composable
fun FindOverlay(
    state: WindowFindUiState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldState = rememberTextFieldState(state.query)
    val focusRequester = remember { FocusRequester() }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { currentOnQueryChange(it) }
    }
    val hasMatches = state.totalMatches > 0
    Row(
        modifier =
            modifier
                .onFocusChanged { onFocusChanged(it.hasFocus) }
                .clip(RoundedCornerShape(6.dp))
                .background(barBackground)
                .border(Dp.Hairline, barBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when {
                            event.key == Key.Escape -> {
                                onClose()
                                true
                            }

                            event.key == Key.Enter -> {
                                onNext()
                                true
                            }

                            event.isCtrlPressed && event.key == Key.F -> {
                                if (event.isShiftPressed) onPrevious() else onNext()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(160.dp)) {
            BasicTextField(
                state = fieldState,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = TextStyle(color = barContent, fontSize = 14.sp),
                cursorBrush = SolidColor(barContent),
                lineLimits = TextFieldLineLimits.SingleLine,
                decorator =
                    TextFieldDecorator { innerTextField ->
                        Box {
                            if (fieldState.text.isEmpty()) {
                                BasicText(
                                    text = "Find in window",
                                    style = TextStyle(color = barContent.copy(alpha = 0.5f), fontSize = 14.sp),
                                )
                            }
                            innerTextField()
                        }
                    },
            )
        }
        val countLabel =
            when {
                state.query.isEmpty() -> ""
                !hasMatches -> "0/0"
                else -> "${state.currentNumber}/${state.totalMatches}"
            }
        if (countLabel.isNotEmpty()) {
            BasicText(text = countLabel, style = TextStyle(color = barContent, fontSize = 13.sp))
        }
        FindIconButton(Res.drawable.keyboard_double_arrow_up, "Next match", onNext, hasMatches)
        FindIconButton(Res.drawable.keyboard_double_arrow_down, "Previous match", onPrevious, hasMatches)
        FindIconButton(Res.drawable.close, "Close find", onClose, enabled = true)
    }
}

@Composable
private fun FindIconButton(
    icon: DrawableResource,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(if (enabled) barContent else barContent.copy(alpha = 0.4f)),
        )
    }
}
