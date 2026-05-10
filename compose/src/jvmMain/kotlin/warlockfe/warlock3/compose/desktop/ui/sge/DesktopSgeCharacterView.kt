package warlockfe.warlock3.compose.desktop.ui.sge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.core.sge.SgeCharacter

@Composable
fun DesktopSgeCharacterView(
    characters: List<SgeCharacter>,
    onBackPress: () -> Unit,
    onCharacterSelect: (SgeCharacter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            characters.forEachIndexed { index, character ->
                Text(
                    text = character.name,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCharacterSelect(character) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                )
                if (index < characters.size - 1) {
                    Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            WarlockOutlinedButton(
                modifier = Modifier.padding(16.dp),
                onClick = onBackPress,
                text = "Back",
            )
        }
    }
}
