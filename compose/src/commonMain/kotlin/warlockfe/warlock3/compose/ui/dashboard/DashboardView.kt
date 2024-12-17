package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.ScrollableColumn

@Composable
fun DashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
) {
    Surface {
        val characters by viewModel.characters.collectAsState(emptyList())
        ScrollableColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = "Characters", style = MaterialTheme.typography.headlineMedium)
            ListItem(
                modifier = Modifier.clickable { connectToSGE() },
                headlineContent = { Text("Connect to SGE") },
                leadingContent = { Icon(imageVector = Icons.Default.Add, contentDescription = null) }
            )
            characters.forEach { character ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.connectCharacter(character) },
                    headlineContent = { Text(character.name) },
                    supportingContent = { Text(character.gameCode) },
                )
            }
        }
    }
}