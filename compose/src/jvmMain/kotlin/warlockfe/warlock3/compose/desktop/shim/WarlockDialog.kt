package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.app_icon
import warlockfe.warlock3.compose.util.ProvideSafeClipboard

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
        // Without an explicit icon the title bar falls back to the JBR default (Windows shows it).
        icon = painterResource(Res.drawable.app_icon),
        state = rememberDialogState(width = width, height = height),
    ) {
        // A dialog is its own Compose scene, so it provides its own clipboard and does not inherit
        // the safe one the app window is running under; see [SafeClipboard].
        ProvideSafeClipboard {
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
            // Scrolls rather than clips: the dialog window is a fixed size, and some of what lands
            // here is arbitrary - a server's error, an exception message - so it can be any length.
            // Taking the leftover space also keeps the buttons at the bottom, as a spacer did.
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(text)
            }
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
