package warlockfe.warlock3.compose.ui.sge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SgeErrorView(message: String, backPressed: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(modifier = Modifier.align(Alignment.Center), text = message)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Button(
                modifier = Modifier.padding(16.dp),
                onClick = backPressed
            ) {
                Text("BACK")
            }
        }
    }
}