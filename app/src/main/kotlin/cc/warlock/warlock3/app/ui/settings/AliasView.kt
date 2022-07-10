package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cc.warlock.warlock3.app.WarlockIcons
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.AliasRepository
import cc.warlock.warlock3.core.prefs.sql.Alias
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterialApi::class, DelicateCoroutinesApi::class)
@Composable
fun AliasView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    aliasRepository: AliasRepository,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id ?: "global"
    val aliases by aliasRepository.observeByCharacter(currentCharacterId).collectAsState(emptyList())
    var editingAlias by remember { mutableStateOf<Alias?>(null) }

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Aliases", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().weight(1f)) {
            aliases.forEach { alias ->
                ListItem(
                    text = {
                        Text(text = alias.pattern)
                    },
                    trailing = {
                        Row {
                            IconButton(
                                onClick = { editingAlias = alias }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    GlobalScope.launch { aliasRepository.deleteById(alias.id) }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
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
                editingAlias = Alias(
                    id = UUID.randomUUID(),
                    characterId = currentCharacterId,
                    pattern = "",
                    replacement = "",
                )
            }) {
                Icon(imageVector = WarlockIcons.Add, contentDescription = null)
            }
        }
    }
    editingAlias?.let { alias ->
        EditAliasDialog(
            alias = alias,
            saveAlias = { newAlias ->
                GlobalScope.launch {
                    aliasRepository.save(newAlias)
                    editingAlias = null
                }
            },
            onClose = { editingAlias = null }
        )
    }
}

@Composable
fun EditAliasDialog(
    alias: Alias,
    saveAlias: (Alias) -> Unit,
    onClose: () -> Unit,
) {
    var pattern by remember(alias.pattern) { mutableStateOf(alias.pattern) }
    var replacement by remember(alias.replacement) { mutableStateOf(alias.replacement) }

    Dialog(
        onCloseRequest = onClose,
        title = "Edit Alias"
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
        ) {
            TextField(value = pattern, label = { Text("Pattern") }, onValueChange = { pattern = it })
            TextField(value = replacement, label = { Text("Replacement") }, onValueChange = { replacement = it })
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
                        saveAlias(
                            Alias(
                                id = alias.id,
                                characterId = alias.characterId,
                                pattern = pattern,
                                replacement = replacement,
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