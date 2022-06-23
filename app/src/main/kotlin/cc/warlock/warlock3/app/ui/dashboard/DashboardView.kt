package cc.warlock.warlock3.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(text = "Characters", style = MaterialTheme.typography.h4)
        val characters = viewModel.characters.collectAsState(emptyList())
        LazyColumn {
            item {
                ListItem(
                    modifier = Modifier.clickable { connectToSGE() },
                    text = { Text("Connect to SGE") },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) }
                )
            }
            items(characters.value) { character ->
                ListItem(
                    modifier = Modifier.clickable { viewModel.connectCharacter(character) },
                    text = { Text(character.name) },
                    secondaryText = { Text(character.gameCode) },
                )
            }
        }
    }
}