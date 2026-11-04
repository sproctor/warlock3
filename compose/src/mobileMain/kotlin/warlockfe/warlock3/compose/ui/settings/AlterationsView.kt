package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import kotlin.uuid.Uuid

@Composable
fun AlterationsView(
    viewModel: AlterationsViewModel,
    allCharacters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val selectedCharacter by viewModel.currentCharacter.collectAsState()
    val alterations by viewModel.alterations.collectAsState()
    var editingAlteration by remember { mutableStateOf<AlterationEntity?>(null) }

    SettingsListScaffold(
        title = "Alterations",
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = viewModel::selectCharacter,
        modifier = modifier.fillMaxSize(),
    ) {
        ScrollableColumn(
            Modifier.fillMaxWidth().weight(1f),
        ) {
            alterations.forEach { alteration ->
                ListItem(
                    headlineContent = {
                        Text(text = alteration.pattern)
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingAlteration = alteration },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.edit),
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.delete(alteration) },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.delete),
                                    contentDescription = "Delete",
                                )
                            }
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingAlteration =
                        AlterationEntity(
                            id = Uuid.random(),
                            characterId = viewModel.characterId(),
                            pattern = "",
                            sourceStream = null,
                            destinationStream = null,
                            result = null,
                            ignoreCase = true,
                            keepOriginal = false,
                        )
                },
                icon = {
                    Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
                },
                text = { Text("New alteration") },
            )
        }
    }
    editingAlteration?.let { alteration ->
        EditAlterationDialog(
            alteration = alteration,
            saveAlteration = { newAlteration ->
                viewModel.save(newAlteration) { editingAlteration = null }
            },
            onClose = { editingAlteration = null },
        )
    }
}

@Composable
fun EditAlterationDialog(
    alteration: AlterationEntity,
    saveAlteration: (AlterationEntity) -> Unit,
    onClose: () -> Unit,
) {
    val pattern = rememberTextFieldState(alteration.pattern)
    val sourceStream = rememberTextFieldState(alteration.sourceStream ?: "")
    val replacement = rememberTextFieldState(alteration.result ?: "")
    var ignoreCase by remember { mutableStateOf(alteration.ignoreCase) }
    var patternError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit alteration") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveAlteration(
                        AlterationEntity(
                            id = alteration.id,
                            characterId = alteration.characterId,
                            pattern = pattern.text.toString(),
                            sourceStream = sourceStream.text.toString().ifBlank { null },
                            destinationStream = alteration.destinationStream,
                            result = replacement.text.toString().ifBlank { null },
                            ignoreCase = ignoreCase,
                            keepOriginal = alteration.keepOriginal,
                        ),
                    )
                },
                enabled = patternError == null && pattern.text.isNotBlank(),
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LaunchedEffect(pattern) {
                    snapshotFlow { pattern.text.toString() }
                        .collectLatest {
                            try {
                                Regex(it)
                                patternError = null
                            } catch (e: Exception) {
                                patternError = e.message
                            }
                        }
                }
                TextField(
                    state = pattern,
                    label = { Text("Pattern") },
                    isError = patternError != null,
                    supportingText = {
                        patternError?.let {
                            Text(it)
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextField(
                    state = replacement,
                    label = { Text("Replacement") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextField(
                    state = sourceStream,
                    label = { Text("Apply alteration to stream (leave blank for any)") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Row {
                    Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                    Text(
                        text = "Ignore case",
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        },
    )
}
