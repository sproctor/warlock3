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
import cc.warlock.warlock3.stormfront.network.SgeCharacter

@Composable
fun SgeCharacterView(
    characters: List<SgeCharacter>,
    onBackPressed: () -> Unit,
    onCharacterSelected: (SgeCharacter) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(characters) { character ->
                CharacterListItem(character = character, onClick = { onCharacterSelected(character) })
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
fun CharacterListItem(character: SgeCharacter, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        text = { Text(character.name) },
    )
}