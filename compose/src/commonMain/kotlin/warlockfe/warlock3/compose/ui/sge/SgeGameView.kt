package warlockfe.warlock3.compose.ui.sge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.sge.SgeGame

@Composable
fun SgeGameView(
    games: List<SgeGame>,
    onBackPressed: () -> Unit,
    onGameSelected: (SgeGame) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(games) { index, game ->
                GameListItem(game = game, onClick = { onGameSelected(game) })
                if (index < games.size - 1)
                    HorizontalDivider()
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

@Composable
fun GameListItem(game: SgeGame, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(game.title) },
    )
}