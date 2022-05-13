package cc.warlock.warlock3.app.ui.settings

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import cc.warlock.warlock3.app.components.DropdownSelect
import cc.warlock.warlock3.core.prefs.models.GameCharacter

@Composable
fun SettingsCharacterSelector(
    selectedCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    onSelect: (GameCharacter?) -> Unit,
    allowGlobal: Boolean = false,
) {
    val list = if (allowGlobal) {
        listOf(null) + characters
    } else {
        characters
    }
    DropdownSelect(
        items = list,
        selected = selectedCharacter,
        onSelect = onSelect,
        label = { Text("Character") },
        itemLabelBuilder = {
            if (it == null) {
                "Global"
            } else {
                "${it.gameCode} ${it.name}"
            }
        }
    )
}