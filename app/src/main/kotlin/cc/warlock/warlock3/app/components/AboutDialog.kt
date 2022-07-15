package cc.warlock.warlock3.app.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.WarlockBuildConfig
import org.jetbrains.skia.Image

@Composable
fun AboutDialog(
    onCloseRequest: () -> Unit,
) {
    Dialog(
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
                    Text("Version: ${WarlockBuildConfig.warlockVersion}")
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