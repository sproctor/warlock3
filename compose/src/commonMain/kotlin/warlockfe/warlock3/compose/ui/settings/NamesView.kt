package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.audio_file
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.generated.resources.palette
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.uuid.Uuid

@Composable
fun NamesView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    nameRepository: NameRepositoryImpl,
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
    val coroutineScope = rememberCoroutineScope()

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
                    leadingContent = {
                        val contentColor = name.textColor.toColor()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = name.backgroundColor.toColor(),
                                    shape = MaterialTheme.shapes.small,
                                )
                                .border(1.dp, contentColor, MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (contentColor.isSpecified) {
                                Icon(
                                    painterResource(Res.drawable.palette),
                                    contentDescription = "Highlight color",
                                    modifier = Modifier.size(20.dp),
                                    tint = contentColor,
                                )
                            }
                        }
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
                                    coroutineScope.launch { nameRepository.deleteById(name.id) }
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
            ExtendedFloatingActionButton(
                onClick = {
                    editingName = NameEntity(
                        id = Uuid.random(),
                        text = "",
                        characterId = currentCharacterId ?: "global",
                        textColor = WarlockColor.Unspecified,
                        backgroundColor = WarlockColor.Unspecified,
                        bold = false,
                        italic = false,
                        underline = false,
                        fontFamily = null,
                        fontSize = null,
                        sound = null,
                    )
                },
                icon = { Icon(painter = painterResource(Res.drawable.add), contentDescription = null) },
                text = { Text("New name") },
            )
        }
    }
    editingName?.let { name ->
        EditNameDialog(
            name = name,
            saveName = { newName ->
                coroutineScope.launch {
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
    val text = rememberTextFieldState(name.text)
    val textColor = rememberTextFieldState(name.textColor.toHexString() ?: "")
    val backgroundColor = rememberTextFieldState(name.backgroundColor.toHexString() ?: "")
    val sound = rememberTextFieldState(name.sound ?: "")

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit name") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveName(
                        name.copy(
                            text = text.text.toString(),
                            textColor = textColor.text.toString().toWarlockColor() ?: WarlockColor.Unspecified,
                            backgroundColor = backgroundColor.text.toString().toWarlockColor() ?: WarlockColor.Unspecified,
                            sound = sound.text.toString().ifBlank { null },
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val hasLowercase = text.text.firstOrNull()?.isLowerCase() == true
                TextField(
                    state = text,
                    label = {
                        Text("Name")
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
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
                        state = textColor,
                    )

                    ColorTextField(
                        modifier = Modifier.weight(1f),
                        label = "Background color",
                        state = backgroundColor,
                    )
                }
                val soundLauncher = rememberFilePickerLauncher { file ->
                    if (file != null) {
                        sound.setTextAndPlaceCursorAtEnd(file.absolutePath())
                    }
                }
                TextField(
                    state = sound,
                    label = { Text("Sound file") },
                    trailingIcon = {
                        IconButton(onClick = { soundLauncher.launch() }) {
                            Icon(
                                painter = painterResource(Res.drawable.audio_file),
                                contentDescription = "Select sound file"
                            )
                        }
                    }
                )
            }
        }
    )
}
