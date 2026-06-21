package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository

@Composable
fun CharactersView(
    allCharacters: List<GameCharacter>,
    characterRepository: CharacterRepository,
    modifier: Modifier = Modifier,
) {
    var deleting by remember { mutableStateOf<GameCharacter?>(null) }
    val coroutineScope = rememberCoroutineScope()

    ScrollableColumn(modifier.fillMaxSize()) {
        allCharacters.forEach { character ->
            ListItem(
                headlineContent = { Text(character.name) },
                supportingContent = { Text(character.gameCode) },
                trailingContent = {
                    IconButton(onClick = { deleting = character }) {
                        Icon(
                            painter = painterResource(Res.drawable.delete),
                            contentDescription = "Delete",
                        )
                    }
                },
            )
        }
    }

    deleting?.let { character ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete character") },
            text = {
                Text(
                    "Delete \"${character.name}\" (${character.gameCode}) and its saved settings reference? " +
                        "This does not affect your play.net account.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            characterRepository.deleteCharacter(character.id)
                            deleting = null
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
