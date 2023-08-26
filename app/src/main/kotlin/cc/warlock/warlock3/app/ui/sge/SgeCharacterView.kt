package cc.warlock.warlock3.app.ui.sge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.stormfront.network.SgeCharacter

@Composable
fun SgeCharacterView(
    characters: List<SgeCharacter>,
    onBackPressed: () -> Unit,
    onCharacterSelected: (SgeCharacter) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(characters) { index, character ->
                CharacterListItem(character = character, onClick = { onCharacterSelected(character) })
                if (index < characters.size - 1)
                    Divider()
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
fun CharacterListItem(character: SgeCharacter, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(character.name) },
    )
}
