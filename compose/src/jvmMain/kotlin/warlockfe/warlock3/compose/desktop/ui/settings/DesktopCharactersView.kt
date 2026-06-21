package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.CharacterRepository

@Composable
fun DesktopCharactersView(
    allCharacters: List<GameCharacter>,
    characterRepository: CharacterRepository,
    modifier: Modifier = Modifier,
) {
    var deleting by remember { mutableStateOf<GameCharacter?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        Text("Characters")
        Spacer(Modifier.height(8.dp))
        if (allCharacters.isEmpty()) {
            Text(
                text = "No saved characters. Characters are saved automatically when you log in.",
                color = JewelTheme.globalColors.text.info,
            )
        }
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            allCharacters.forEach { character ->
                WarlockListItem(
                    headline = {
                        Column {
                            Text(character.name)
                            Text(
                                text = character.gameCode,
                                color = JewelTheme.globalColors.text.info,
                            )
                        }
                    },
                    trailing = {
                        WarlockOutlinedButton(
                            onClick = { deleting = character },
                            text = "Delete",
                        )
                    },
                )
            }
        }
    }

    deleting?.let { character ->
        DesktopConfirmationDialog(
            title = "Delete character",
            text =
                "Delete \"${character.name}\" (${character.gameCode}) and its saved settings reference? " +
                    "This does not affect your play.net account.",
            onDismiss = { deleting = null },
            onConfirm = {
                scope.launch {
                    characterRepository.deleteCharacter(character.id)
                    deleting = null
                }
            },
        )
    }
}
