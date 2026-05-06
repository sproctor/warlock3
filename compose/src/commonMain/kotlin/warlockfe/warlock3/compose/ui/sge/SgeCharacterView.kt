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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.sge.SgeCharacter

@Composable
fun SgeCharacterView(
    characters: List<SgeCharacter>,
    onBackPress: () -> Unit,
    onCharacterSelect: (SgeCharacter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(characters) { index, character ->
                CharacterListItem(character = character, onClick = { onCharacterSelect(character) })
                if (index < characters.size - 1) {
                    HorizontalDivider()
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                modifier = Modifier.padding(16.dp),
                onClick = onBackPress,
            ) {
                Text("BACK")
            }
        }
    }
}

@Composable
fun CharacterListItem(
    character: SgeCharacter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(character.name) },
    )
}
