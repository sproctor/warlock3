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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.AlterationRepository
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import java.util.*
import java.util.regex.PatternSyntaxException

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
    var editingAlteration by remember { mutableStateOf<AlterationEntity?>(null) }

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
        ScrollableColumn(
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
                                    painter = painterResource(Res.drawable.edit),
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
                editingAlteration = AlterationEntity(
                    id = UUID.randomUUID(),
                    characterId = currentCharacterId,
                    pattern = "",
                    sourceStream = null,
                    destinationStream = null,
                    result = null,
                    ignoreCase = true,
                    keepOriginal = false,
                )
            }) {
                Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
            }
        }
    }
    val scope = rememberCoroutineScope()
    editingAlteration?.let { alteration ->
        EditAlterationDialog(
            alteration = alteration,
            saveAlteration = { newAlteration ->
                scope.launch {
                    alterationRepository.save(newAlteration)
                    editingAlteration = null
                }
            },
            onClose = { editingAlteration = null }
        )
    }
}

@Composable
fun EditAlterationDialog(
    alteration: AlterationEntity,
    saveAlteration: (AlterationEntity) -> Unit,
    onClose: () -> Unit,
) {
    var pattern by remember { mutableStateOf(alteration.pattern) }
    var sourceStream by remember { mutableStateOf(alteration.sourceStream ?: "") }
    var destinationStream by remember { mutableStateOf(alteration.destinationStream ?: "") }
    var replacement by remember { mutableStateOf(alteration.result ?: "") }
    var keepOriginal by remember { mutableStateOf(alteration.keepOriginal) }
    var ignoreCase by remember { mutableStateOf(alteration.ignoreCase) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Highlight") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveAlteration(
                        AlterationEntity(
                            id = alteration.id,
                            characterId = alteration.characterId,
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
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                TextField(
                    value = replacement,
                    label = { Text("Replacement") },
                    onValueChange = { replacement = it },
                )
                TextField(
                    value = sourceStream,
                    label = { Text("Apply alteration to stream (leave blank for any)") },
                    onValueChange = { sourceStream = it },
                )
//                TextField(
//                    value = destinationStream,
//                    label = { Text("Destination stream (leave blank to leave text on the source stream)") },
//                    onValueChange = {
//                        destinationStream = it
//                        if (it.isBlank()) {
//                            keepOriginal = false
//                        }
//                    }
//                )
                Row {
                    Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                    Text(
                        text = "Ignore case",
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
//                if (destinationStream.isNotBlank()) {
//                    Row {
//                        Checkbox(checked = keepOriginal, onCheckedChange = { keepOriginal = it })
//                        Text(
//                            text = "Keep original text",
//                            modifier = Modifier.align(Alignment.CenterVertically)
//                        )
//                    }
//                }
            }
        }
    )
}
