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
import warlockfe.warlock3.core.prefs.models.AliasEntity
import kotlin.uuid.Uuid

@Composable
fun AliasView(
    viewModel: AliasesViewModel,
    allCharacters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val selectedCharacter by viewModel.currentCharacter.collectAsState()
    val aliases by viewModel.aliases.collectAsState()
    var editingAlias by remember { mutableStateOf<AliasEntity?>(null) }

    SettingsListScaffold(
        title = "Aliases",
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = viewModel::selectCharacter,
        modifier = modifier.fillMaxSize(),
    ) {
        ScrollableColumn(Modifier.fillMaxWidth().weight(1f)) {
            aliases.forEach { alias ->
                ListItem(
                    headlineContent = {
                        Text(text = alias.pattern)
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingAlias = alias },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.edit),
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.delete(alias) },
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
                    editingAlias =
                        AliasEntity(
                            id = Uuid.random(),
                            characterId = viewModel.characterId(),
                            pattern = "",
                            replacement = "",
                        )
                },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.add),
                        contentDescription = null,
                    )
                },
                text = { Text("New alias") },
            )
        }
    }
    editingAlias?.let { alias ->
        EditAliasDialog(
            alias = alias,
            saveAlias = { newAlias ->
                viewModel.save(newAlias) { editingAlias = null }
            },
            onClose = { editingAlias = null },
        )
    }
}

@Composable
fun EditAliasDialog(
    alias: AliasEntity,
    saveAlias: (AliasEntity) -> Unit,
    onClose: () -> Unit,
) {
    val pattern = rememberTextFieldState(alias.pattern)
    val replacement = rememberTextFieldState(alias.replacement)
    var patternError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(
                onClick = {
                    saveAlias(
                        AliasEntity(
                            id = alias.id,
                            characterId = alias.characterId,
                            pattern = pattern.text.toString(),
                            replacement = replacement.text.toString(),
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
        title = { Text("Edit Alias") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LaunchedEffect(Unit) {
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
            }
        },
    )
}
