package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.CharacterSettingsRepository
import cc.warlock.warlock3.core.prefs.defaultMaxScrollLines
import cc.warlock.warlock3.core.prefs.scrollbackKey
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun GeneralSettingsView(
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter ?: characters.firstOrNull()) }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created")
        return
    }

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth()) {
            val scrollbarStyle = LocalScrollbarStyle.current
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(end = scrollbarStyle.thickness)
                    .verticalScroll(scrollState)
                    .fillMaxWidth(),
            ) {
                val maxLines by characterSettingsRepository.observe(
                    characterId = currentCharacter.id, key = scrollbackKey
                ).collectAsState(null)
                TextField(
                    value = maxLines ?: defaultMaxScrollLines.toString(),
                    onValueChange = {
                        GlobalScope.launch {
                            characterSettingsRepository.save(
                                characterId = currentCharacter.id, key = scrollbackKey, value = it
                            )
                        }
                    },
                    label = {
                        Text("Maximum lines in scroll back buffer")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState),
                style = scrollbarStyle.copy(
                    hoverColor = MaterialTheme.colors.primary,
                    unhoverColor = MaterialTheme.colors.primary.copy(alpha = 0.42f)
                )
            )
        }
    }
}