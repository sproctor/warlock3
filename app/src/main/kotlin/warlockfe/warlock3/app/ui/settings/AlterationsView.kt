package warlockfe.warlock3.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import warlockfe.warlock3.app.ui.theme.WarlockIcons
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.models.Alteration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AlterationsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    alterationRepository: AlterationRepository,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id ?: "global"
    val alterations by alterationRepository.observeByCharacter(currentCharacterId)
        .collectAsState(emptyList())
    var editingAlteration by remember { mutableStateOf<Alteration?>(null) }

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Alterations", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f)
        ) {
            alterations.forEach { alteration ->
                ListItem(
                    headlineContent = {
                        Text(text = alteration.pattern)
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingAlteration = alteration }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    GlobalScope.launch { alterationRepository.deleteById(alteration.id) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
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
                editingAlteration = Alteration(
                    id = UUID.randomUUID(),
                    pattern = "",
                    sourceStream = null,
                    destinationStream = null,
                    result = null,
                    ignoreCase = true,
                    keepOriginal = false,
                )
            }) {
                Icon(imageVector = WarlockIcons.Add, contentDescription = null)
            }
        }
    }
    editingAlteration?.let { alteration ->
        EditAlterationDialog(
            alteration = alteration,
            saveAlteration = { newAlteration ->
                runBlocking {
                    alterationRepository.save(currentCharacterId, newAlteration)
                    editingAlteration = null
                }
            },
            onClose = { editingAlteration = null }
        )
    }
}

@Composable
fun EditAlterationDialog(
    alteration: Alteration,
    saveAlteration: (Alteration) -> Unit,
    onClose: () -> Unit,
) {
    var pattern by remember { mutableStateOf(alteration.pattern) }
    var sourceStream by remember { mutableStateOf(alteration.sourceStream ?: "") }
    var destinationStream by remember { mutableStateOf(alteration.destinationStream ?: "") }
    var replacement by remember { mutableStateOf(alteration.result ?: "") }
    var keepOriginal by remember { mutableStateOf(alteration.keepOriginal) }
    var ignoreCase by remember { mutableStateOf(alteration.ignoreCase) }

    DialogWindow(
        onCloseRequest = onClose,
        state = rememberDialogState(width = 640.dp, height = 480.dp),
        title = "Edit Highlight",
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            TextField(value = pattern, label = { Text("Pattern") }, onValueChange = { pattern = it })
            Spacer(Modifier.height(16.dp))
            TextField(value = replacement, label = { Text("Replacement") }, onValueChange = { replacement = it })
            Spacer(Modifier.height(16.dp))
            TextField(
                value = sourceStream,
                label = { Text("Source stream (leave blank for any)") },
                onValueChange = { sourceStream = it })
            Spacer(Modifier.height(16.dp))
            TextField(
                value = destinationStream,
                label = { Text("Destination stream (leave blank to leave text on the source stream)") },
                onValueChange = {
                    destinationStream = it
                    if (it.isBlank()) {
                        keepOriginal = false
                    }
                }
            )
            Spacer(Modifier.height(16.dp))
            Row {
                Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                Text(text = "Ignore case", modifier = Modifier.align(Alignment.CenterVertically))
            }
            if (destinationStream.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Row {
                    Checkbox(checked = keepOriginal, onCheckedChange = { keepOriginal = it })
                    Text(text = "Keep original text", modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onClose) {
                    Text("CANCEL")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        saveAlteration(
                            Alteration(
                                id = alteration.id,
                                pattern = pattern,
                                sourceStream = sourceStream,
                                destinationStream = destinationStream,
                                result = replacement.ifBlank { null },
                                ignoreCase = ignoreCase,
                                keepOriginal = keepOriginal
                            )
                        )
                    }
                ) {
                    Text("OK")
                }
            }
        }
    }
}