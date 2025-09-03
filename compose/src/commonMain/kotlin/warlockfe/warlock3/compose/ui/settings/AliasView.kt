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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.AliasRepository
import warlockfe.warlock3.core.prefs.models.AliasEntity
import java.util.*
import java.util.regex.PatternSyntaxException

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AliasView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    aliasRepository: AliasRepository,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id ?: "global"
    val aliases by aliasRepository.observeByCharacter(currentCharacterId).collectAsState(emptyList())
    var editingAlias by remember { mutableStateOf<AliasEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Aliases", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().weight(1f)) {
            aliases.forEach { alias ->
                ListItem(
                    headlineContent = {
                        Text(text = alias.pattern)
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingAlias = alias }
                            ) {
                                Icon(painter = painterResource(Res.drawable.edit), contentDescription = "Edit")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    GlobalScope.launch { aliasRepository.deleteById(alias.id) }
                                }
                            ) {
                                Icon(painter = painterResource(Res.drawable.delete), contentDescription = "Delete")
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
                editingAlias = AliasEntity(
                    id = UUID.randomUUID(),
                    characterId = currentCharacterId,
                    pattern = "",
                    replacement = "",
                )
            }) {
                Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
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
    alias: AliasEntity,
    saveAlias: (AliasEntity) -> Unit,
    onClose: () -> Unit,
) {
    var pattern by remember(alias.pattern) { mutableStateOf(alias.pattern) }
    var replacement by remember(alias.replacement) { mutableStateOf(alias.replacement) }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(
                onClick = {
                    saveAlias(
                        AliasEntity(
                            id = alias.id,
                            characterId = alias.characterId,
                            pattern = pattern,
                            replacement = replacement,
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                var patternError by remember { mutableStateOf<String?>(null) }
                TextField(
                    value = pattern,
                    label = { Text("Pattern") },
                    onValueChange = {
                        pattern = it
                        try {
                            Regex(it)
                            patternError = null
                        } catch (e: PatternSyntaxException) {
                            patternError = e.message
                        }
                    },
                    isError = patternError != null,
                    supportingText = {
                        patternError?.let {
                            Text(it)
                        }
                    }
                )
                TextField(value = replacement, label = { Text("Replacement") }, onValueChange = { replacement = it })
            }
        }
    )
}