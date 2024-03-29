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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.skia.Image

@Composable
fun AboutDialog(
    warlockVersion: String,
    onCloseRequest: () -> Unit,
) {
    DialogWindow(
        state = rememberDialogState(width = 400.dp, height = 300.dp),
        onCloseRequest = onCloseRequest,
        title = "About Warlock 3",
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                val image = remember {
                    Image.makeFromEncoded(
                        javaClass.getResourceAsStream("/images/icon.png")!!.readBytes()
                    ).toComposeImageBitmap() }
                Image(modifier = Modifier.size(48.dp), bitmap = image, contentDescription = "Warlock logo")
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Warlock 3")
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