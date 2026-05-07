package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun WarlockDialog(
    title: String,
    onCloseRequest: () -> Unit,
    width: Dp = 400.dp,
    height: Dp = 300.dp,
    content: @Composable () -> Unit,
) {
    DialogWindow(
        title = title,
        onCloseRequest = onCloseRequest,
        state = rememberDialogState(width = width, height = height),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground)
                    .padding(16.dp),
        ) {
            content()
        }
    }
}

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun WarlockAlertDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    width: Dp = 400.dp,
    height: Dp = 200.dp,
) {
    WarlockDialog(
        title = title,
        onCloseRequest = onDismissRequest,
        width = width,
        height = height,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text)
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = androidx.compose.ui.Alignment.End),
            ) {
                if (dismissButton != null) {
                    dismissButton()
                }
                confirmButton()
            }
        }
    }
}
