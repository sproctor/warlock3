package cc.warlock.warlock3.app.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DashboardView(
    connectToSGE: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        OutlinedButton(
            onClick = connectToSGE,
        ) {
            Text("Connect by SGE")
        }
        Text(text = "Accounts", style = MaterialTheme.typography.h2)
        LazyColumn {

        }
    }
}