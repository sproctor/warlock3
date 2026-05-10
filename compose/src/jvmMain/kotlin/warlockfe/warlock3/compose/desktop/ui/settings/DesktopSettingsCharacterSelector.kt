package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockDropdownSelect
import warlockfe.warlock3.core.client.GameCharacter

@Composable
fun DesktopSettingsCharacterSelector(
    selectedCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    onSelect: (GameCharacter?) -> Unit,
    modifier: Modifier = Modifier,
    allowGlobal: Boolean = false,
) {
    val list =
        if (allowGlobal) {
            listOf<GameCharacter?>(null) + characters
        } else {
            characters
        }
    Column(modifier = modifier) {
        Text("Character")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.width(0.dp))
            WarlockDropdownSelect(
                items = list,
                selected = selectedCharacter,
                onSelect = onSelect,
                itemLabelBuilder = {
                    if (it == null) "Global" else "${it.gameCode} ${it.name}"
                },
            )
        }
    }
}
