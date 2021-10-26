package cc.warlock.warlock3.app.components

import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.*

@Composable
fun CharacterSelector(
    currentId: String?,
    characters: Map<String?, String>, // map of characterId to name/description
    onCharacterSelect: (String?) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showMenu = !showMenu}) {
        val name = characters[currentId]
        Text("Selected: $name")
    }
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        characters.forEach { (characterId, name) ->
            DropdownMenuItem(onClick = { onCharacterSelect(characterId) }) {
                Text(name)
            }
        }
    }
}