package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.util.DirectoryChooserButton
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.defaultMaxScrollLines
import warlockfe.warlock3.core.prefs.defaultMaxTypeAhead
import warlockfe.warlock3.core.prefs.maxTypeAheadKey
import warlockfe.warlock3.core.prefs.scrollbackKey

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun GeneralSettingsView(
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    scriptDirRepository: ScriptDirRepository,
    clientSettingRepository: ClientSettingRepository,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter) }
    val currentCharacter = currentCharacterState.value
    val currentCharacterId = currentCharacter?.id ?: "global"

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
            allowGlobal = true
        )
        Spacer(Modifier.height(16.dp))
        ScrollableColumn {
            if (currentCharacter != null) {
                val initialMaxLines by characterSettingsRepository.observe(
                    characterId = currentCharacter.id, key = scrollbackKey
                ).collectAsState(null)
                var maxLinesValue by remember(initialMaxLines == null) {
                    mutableStateOf(
                        TextFieldValue(initialMaxLines ?: defaultMaxScrollLines.toString())
                    )
                }
                TextField(
                    value = maxLinesValue,
                    onValueChange = {
                        maxLinesValue = it
                        // TODO: use a view model here to handle scope
                        GlobalScope.launch {
                            characterSettingsRepository.save(
                                characterId = currentCharacter.id,
                                key = scrollbackKey,
                                value = it.text
                            )
                        }
                    },
                    label = {
                        Text("Maximum lines in scroll back buffer")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(16.dp))

                val initialMaxTypeAhead by characterSettingsRepository.observe(
                    characterId = currentCharacter.id, key = maxTypeAheadKey
                ).collectAsState(null)
                var maxTypeAheadValue by remember(initialMaxTypeAhead == null) {
                    mutableStateOf(
                        TextFieldValue(initialMaxTypeAhead ?: defaultMaxTypeAhead.toString())
                    )
                }
                TextField(
                    value = maxTypeAheadValue,
                    onValueChange = {
                        maxTypeAheadValue = it
                        // TODO: use a view model here to handle scope
                        GlobalScope.launch {
                            characterSettingsRepository.save(
                                characterId = currentCharacter.id,
                                key = maxTypeAheadKey,
                                value = it.text
                            )
                        }
                    },
                    label = {
                        Text("Maximum commands to type ahead. 0 to disable buffer")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("Script directories", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val scriptDirs by scriptDirRepository.observeScriptDirs(characterId = currentCharacterId)
                .collectAsState(emptyList())
            Column(
                Modifier.border(Dp.Hairline, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(scriptDirRepository.getDefaultDir()) }
                )
                scriptDirs.forEach { scriptDir ->
                    ListItem(
                        headlineContent = {
                            Text(scriptDir)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    GlobalScope.launch {
                                        scriptDirRepository.delete(
                                            characterId = currentCharacterId,
                                            path = scriptDir
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete"
                                )
                            }
                        },
                    )
                }
            }

            DirectoryChooserButton(
                label = "Add a directory",
                title = "Choose a script directory",
                saveDirectory = {
                    scriptDirRepository.save(currentCharacterId, it)
                }
            )

            Spacer(Modifier.height(16.dp))

            val currentTheme by clientSettingRepository.observeTheme().collectAsState(null)
            Text("Select theme")
            val scope = rememberCoroutineScope()
            ThemeSetting.entries.forEach { entry ->
                Row(
                    Modifier.clickable {
                        scope.launch {
                            clientSettingRepository.putTheme(entry)
                        }
                    }
                ) {
                    RadioButton(
                        selected = currentTheme == entry,
                        onClick = {
                            scope.launch {
                                clientSettingRepository.putTheme(entry)
                            }
                        }
                    )
                    Text(entry.name)
                }
            }

//            Spacer(Modifier.height(8.dp))
//            Text("UI scale", style = MaterialTheme.typography.headlineSmall)
//
//            val scope = rememberCoroutineScope()
//            val initialScale by clientSettingRepository.observeScale().collectAsState(null)
//            val sliderState = remember(initialScale) {
//                SliderState(
//                    value = initialScale ?: 1f,
//                    valueRange = 0.25f..3f,
//                    steps = 54,
//                ).apply {
//                    onValueChangeFinished = {
//                        scope.launch {
//                            clientSettingRepository.putScale(value)
//                        }
//                    }
//                }
//            }
//            Slider(
//                state = sliderState,
//            )
//            // TODO: round to nearest hundredth
//            Text("%.2f".format(sliderState.value))
        }
    }
}