package cc.warlock.warlock3.app.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.stormfront.network.SgeGame

@Composable
fun SgeGameView(
    games: List<SgeGame>,
    onBackPressed: () -> Unit,
    onGameSelected: (SgeGame) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(games) { game ->
                GameListItem(game = game, onClick = { onGameSelected(game) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                modifier = Modifier.padding(16.dp),
                onClick = onBackPressed
            ) {
                Text("BACK")
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun GameListItem(game: SgeGame, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        text = { Text(game.title) },
    )
}