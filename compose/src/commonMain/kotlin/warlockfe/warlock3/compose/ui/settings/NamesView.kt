package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.NameRepository
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor
import java.util.*

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun NamesView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    nameRepository: NameRepository,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id
    val names by if (currentCharacterId == null) {
        nameRepository.observeGlobal()
    } else {
        nameRepository.observeByCharacter(currentCharacterId)
    }
        .collectAsState(emptyList())
    var editingName by remember { mutableStateOf<NameEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Names", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        ScrollableColumn(
            Modifier.fillMaxWidth().weight(1f)
        ) {
            names.forEach { name ->
                ListItem(
                    headlineContent = {
                        Text(text = name.text)
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingName = name }
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.edit),
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    scope.launch { nameRepository.deleteById(name.id) }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.delete),
                                    contentDescription = "Delete",
                                )
                            }
                        }
                    }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = {
                editingName = NameEntity(
                    id = UUID.randomUUID(),
                    text = "",
                    characterId = currentCharacterId ?: "global",
                    textColor = WarlockColor.Unspecified,
                    backgroundColor = WarlockColor.Unspecified,
                    bold = false,
                    italic = false,
                    underline = false,
                    fontFamily = null,
                    fontSize = null,
                )
            }) {
                Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
            }
        }
    }
    editingName?.let { name ->
        EditNameDialog(
            name = name,
            saveName = { newName ->
                scope.launch {
                    nameRepository.save(newName)
                    editingName = null
                }
            },
            onClose = { editingName = null }
        )
    }
}

@Composable
fun EditNameDialog(
    name: NameEntity,
    saveName: (NameEntity) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember(name.text) { mutableStateOf(name.text) }
    var textColorValue by remember {
        mutableStateOf(name.textColor.toHexString() ?: "")
    }
    var backgroundColorValue by remember {
        mutableStateOf(name.backgroundColor.toHexString() ?: "")
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Highlight") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveName(
                        name.copy(
                            text = text,
                            textColor = textColorValue.toWarlockColor() ?: WarlockColor.Unspecified,
                            backgroundColor = backgroundColorValue.toWarlockColor() ?: WarlockColor.Unspecified,
                        )
                    )
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                val hasLowercase = text.firstOrNull()?.isLowerCase() == true
                TextField(
                    value = text,
                    label = {
                        Text("Name")
                    },
                    singleLine = true,
                    onValueChange = { text = it },
                    supportingText = {
                        if (hasLowercase) {
                            Text("First letter of name is lowercase")
                        }
                    },
//                    leadingIcon = {
//                        if (hasLowercase) {
//
//                        }
//                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorTextField(
                        modifier = Modifier.weight(1f),
                        label = "Text color",
                        value = textColorValue,
                        onValueChanged = {
                            textColorValue = it
                        }
                    )

                    ColorTextField(
                        modifier = Modifier.weight(1f),
                        label = "Background color",
                        value = backgroundColorValue,
                        onValueChanged = {
                            backgroundColorValue = it
                        }
                    )
                }
            }
        }
    )
}
