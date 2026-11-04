package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.CheckboxRow
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.Ignore
import warlockfe.warlock3.core.prefs.models.IgnoreMatchMode
import kotlin.uuid.Uuid

@Composable
fun IgnoresView(
    viewModel: IgnoresViewModel,
    allCharacters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val selectedCharacter by viewModel.currentCharacter.collectAsState()
    val ignores by viewModel.ignores.collectAsState()
    var editingIgnore by remember { mutableStateOf<Ignore?>(null) }

    SettingsListScaffold(
        title = "Ignores",
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = viewModel::selectCharacter,
        modifier = modifier.fillMaxSize(),
    ) {
        ScrollableColumn(
            Modifier.fillMaxWidth().weight(1f),
        ) {
            ignores.forEach { ignore ->
                ListItem(
                    headlineContent = {
                        Text(text = ignore.pattern)
                    },
                    supportingContent = {
                        Text(text = ignore.matchSummary())
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingIgnore = ignore },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.edit),
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.delete(ignore) },
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
                    editingIgnore =
                        Ignore(
                            id = Uuid.random(),
                            pattern = "",
                            isRegex = false,
                            matchMode = IgnoreMatchMode.CONTAINS,
                            ignoreCase = true,
                        )
                },
                icon = {
                    Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
                },
                text = { Text("New ignore") },
            )
        }
    }
    editingIgnore?.let { ignore ->
        EditIgnoreDialog(
            ignore = ignore,
            saveIgnore = { newIgnore ->
                viewModel.save(newIgnore) { editingIgnore = null }
            },
            onClose = { editingIgnore = null },
        )
    }
}

@Composable
fun EditIgnoreDialog(
    ignore: Ignore,
    saveIgnore: (Ignore) -> Unit,
    onClose: () -> Unit,
) {
    val pattern = rememberTextFieldState(ignore.pattern)
    var isRegex by remember { mutableStateOf(ignore.isRegex) }
    var matchMode by remember { mutableStateOf(ignore.matchMode) }
    var ignoreCase by remember { mutableStateOf(ignore.ignoreCase) }

    // Only a regex ignore needs a valid regex; a text ignore may freely contain regex metacharacters.
    val patternError =
        if (isRegex) {
            try {
                Regex(pattern.text.toString())
                null
            } catch (e: Exception) {
                e.message ?: "Invalid regex"
            }
        } else {
            null
        }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit ignore") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveIgnore(
                        Ignore(
                            id = ignore.id,
                            pattern = pattern.text.toString(),
                            isRegex = isRegex,
                            matchMode = matchMode,
                            ignoreCase = ignoreCase,
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.selectableGroup()) {
                    Row(
                        Modifier.selectable(
                            selected = !isRegex,
                            onClick = { isRegex = false },
                            role = Role.RadioButton,
                        ),
                    ) {
                        RadioButton(
                            selected = !isRegex,
                            onClick = null,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Text ignore",
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Row(
                        Modifier.selectable(
                            selected = isRegex,
                            onClick = { isRegex = true },
                            role = Role.RadioButton,
                        ),
                    ) {
                        RadioButton(
                            selected = isRegex,
                            onClick = null,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Regex ignore",
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                TextField(
                    state = pattern,
                    label = { Text("Pattern") },
                    isError = patternError != null,
                    supportingText = {
                        if (patternError != null) {
                            Text("Error: $patternError")
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                if (!isRegex) {
                    Column(Modifier.selectableGroup()) {
                        matchModeOptions.forEach { (mode, label) ->
                            Row(
                                Modifier.selectable(
                                    selected = matchMode == mode,
                                    onClick = { matchMode = mode },
                                    role = Role.RadioButton,
                                ),
                            ) {
                                RadioButton(
                                    selected = matchMode == mode,
                                    onClick = null,
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = label,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                            }
                        }
                    }
                }
                CheckboxRow(
                    checked = !ignoreCase,
                    onCheckedChange = { ignoreCase = !it },
                    text = "Case sensitive",
                )
            }
        },
    )
}

private val matchModeOptions =
    listOf(
        IgnoreMatchMode.CONTAINS to "Match anywhere in line",
        IgnoreMatchMode.WORD to "Match whole word",
        IgnoreMatchMode.LINE to "Match entire line",
    )

private fun Ignore.matchSummary(): String =
    buildList {
        if (isRegex) {
            add("Regex")
        } else {
            add(
                when (matchMode) {
                    IgnoreMatchMode.CONTAINS -> "Anywhere in line"
                    IgnoreMatchMode.WORD -> "Whole word"
                    IgnoreMatchMode.LINE -> "Entire line"
                },
            )
        }
        if (!ignoreCase) add("Case sensitive")
    }.joinToString(" · ")
