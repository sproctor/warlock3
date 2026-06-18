package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.client.GameCharacter

/**
 * The shared preamble for the per-character settings list screens: a character selector followed by
 * a title. Each screen supplies its own list and action buttons as [content] (a [ColumnScope] so the
 * list can take the remaining height with `Modifier.weight(1f)`).
 */
@Composable
fun SettingsListScaffold(
    title: String,
    selectedCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    onSelectCharacter: (GameCharacter?) -> Unit,
    modifier: Modifier = Modifier,
    allowGlobal: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        SettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = characters,
            onSelect = onSelectCharacter,
            allowGlobal = allowGlobal,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
