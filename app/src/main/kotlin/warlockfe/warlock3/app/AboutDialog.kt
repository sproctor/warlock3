package warlockfe.warlock3.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.app.resources.Res
import warlockfe.warlock3.app.resources.icon

@Composable
fun AboutDialog(
    onCloseRequest: () -> Unit,
) {
    val warlockVersion = System.getProperty("app.version", "development")
    DialogWindow(
        state = rememberDialogState(width = 400.dp, height = 300.dp),
        onCloseRequest = onCloseRequest,
        title = "About Warlock",
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Image(
                    modifier = Modifier.size(50.dp),
                    painter = painterResource(Res.drawable.icon),
                    contentDescription = "Warlock logo",
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Warlock FE")
                    Text("Version: $warlockVersion")
                }
            }
            Box(Modifier.fillMaxSize()) {
                Button(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    onClick = onCloseRequest
                ) {
                    Text("OK")
                }
            }
        }
    }
}