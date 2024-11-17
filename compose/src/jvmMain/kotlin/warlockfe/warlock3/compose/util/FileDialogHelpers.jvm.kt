package warlockfe.warlock3.compose.util

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JFileChooser

@Composable
actual fun DirectoryChooserButton(label: String, title: String, saveDirectory: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val window = LocalWindowComponent.current
    Button(onClick = {
        scope.launch(Dispatchers.IO) {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.dialogTitle = title
            chooser.isAcceptAllFileFilterUsed = false
            if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                saveDirectory(chooser.selectedFile.absolutePath)
            }
        }
    }) {
        Text(label)
    }
}
