package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.client.GameCharacter

/**
 * The shared preamble for the desktop per-character settings list screens: a character selector above
 * the screen's own content. The page title is already shown by the settings dialog's nav, so it is not
 * repeated here. Each screen supplies its own list and action buttons as [content] (a [ColumnScope] so
 * the list can take the remaining height with `Modifier.weight(1f)`).
 */
@Composable
fun SettingsListScaffold(
    selectedCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    onSelectCharacter: (GameCharacter?) -> Unit,
    modifier: Modifier = Modifier,
    allowGlobal: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = characters,
            onSelect = onSelectCharacter,
            allowGlobal = allowGlobal,
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}
