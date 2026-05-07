package warlockfe.warlock3.compose.ui.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import warlockfe.warlock3.compose.components.DropdownSelect
import warlockfe.warlock3.core.client.GameCharacter

@Composable
fun SettingsCharacterSelector(
    selectedCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    onSelect: (GameCharacter?) -> Unit,
    modifier: Modifier = Modifier,
    allowGlobal: Boolean = false,
) {
    val list =
        if (allowGlobal) {
            listOf(null) + characters
        } else {
            characters
        }
    DropdownSelect(
        items = list,
        selected = selectedCharacter,
        onSelect = onSelect,
        modifier = modifier,
        label = { Text("Character") },
        itemLabelBuilder = {
            if (it == null) {
                "Global"
            } else {
                "${it.gameCode} ${it.name}"
            }
        },
    )
}
