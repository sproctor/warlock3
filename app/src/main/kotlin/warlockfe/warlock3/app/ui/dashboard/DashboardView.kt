package warlockfe.warlock3.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(text = "Characters", style = MaterialTheme.typography.headlineMedium)
        val characters = viewModel.characters.collectAsState(emptyList())
        LazyColumn {
            item {
                ListItem(
                    modifier = Modifier.clickable { connectToSGE() },
                    headlineContent = { Text("Connect to SGE") },
                    leadingContent = { Icon(imageVector = Icons.Default.Add, contentDescription = null) }
                )
            }
            items(characters.value) { character ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.connectCharacter(character) },
                    headlineContent = { Text(character.name) },
                    supportingContent = { Text(character.gameCode) },
                )
            }
        }
    }
}