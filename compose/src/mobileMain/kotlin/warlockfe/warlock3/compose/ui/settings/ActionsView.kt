package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.generated.resources.keyboard_double_arrow_down
import warlockfe.warlock3.compose.generated.resources.keyboard_double_arrow_up
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.Action
import warlockfe.warlock3.core.prefs.repositories.ActionRepository
import kotlin.uuid.Uuid

/**
 * Edits the per-character action buttons. The top section is the toolbar (the ordered buttons shown
 * on the game screen, a list of references into the pool); below it is the pool of all defined
 * actions. A leaf action runs a WSL script; a group references other actions and opens a drill-down
 * menu when pressed.
 */
@Composable
fun ActionsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    actionRepository: ActionRepository,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id ?: "global"
    val pool by actionRepository.observePool(currentCharacterId).collectAsState(emptyList())
    val toolbar by actionRepository.observeToolbar(currentCharacterId).collectAsState(emptyList())
    val poolById = remember(pool) { pool.associateBy { it.id } }
    var editingAction by remember { mutableStateOf<Action?>(null) }
    val scope = rememberCoroutineScope()

    SettingsListScaffold(
        title = "Actions",
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = { selectedCharacter = it },
        modifier = modifier.fillMaxSize(),
    ) {
        ScrollableColumn(Modifier.fillMaxWidth().weight(1f)) {
            Text("Button bar", style = MaterialTheme.typography.titleMedium)
            ActionIdListEditor(
                ids = toolbar,
                pool = poolById,
                candidates = pool.filterNot { it.id in toolbar },
                emptyText = "No buttons yet. Add an action below to the bar.",
                onChange = { scope.launch { actionRepository.setToolbar(currentCharacterId, it) } },
            )

            Spacer(Modifier.height(24.dp))
            Text("All actions", style = MaterialTheme.typography.titleMedium)
            if (pool.isEmpty()) {
                Text(
                    "No actions defined yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            pool.forEach { action ->
                ListItem(
                    leadingContent = groupIndicator(action),
                    headlineContent = { Text(action.name.ifBlank { "(unnamed)" }) },
                    supportingContent = { Text(if (action.isGroup) "Group" else "Script") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editingAction = action }) {
                                Icon(painterResource(Res.drawable.edit), contentDescription = "Edit")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { scope.launch { actionRepository.deleteAction(currentCharacterId, action.id) } },
                            ) {
                                Icon(painterResource(Res.drawable.delete), contentDescription = "Delete")
                            }
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            OutlinedButton(
                onClick = { editingAction = Action(id = Uuid.random(), name = "", script = null) },
            ) {
                Icon(painterResource(Res.drawable.add), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New group")
            }
            Button(
                onClick = { editingAction = Action(id = Uuid.random(), name = "", script = "") },
            ) {
                Icon(painterResource(Res.drawable.add), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New action")
            }
        }
    }

    editingAction?.let { action ->
        EditActionDialog(
            action = action,
            pool = pool,
            onSave = { updated ->
                scope.launch {
                    actionRepository.saveAction(currentCharacterId, updated)
                    editingAction = null
                }
            },
            onClose = { editingAction = null },
        )
    }
}

private fun groupIndicator(action: Action): (@Composable () -> Unit)? =
    if (action.isGroup) {
        { Icon(painterResource(Res.drawable.arrow_right), contentDescription = null) }
    } else {
        null
    }

/**
 * Edits an ordered list of action ids (the toolbar, or a group's children): each entry can be moved
 * up/down or removed, and an "Add" dropdown appends an action picked from [candidates].
 */
@Composable
private fun ActionIdListEditor(
    ids: List<Uuid>,
    pool: Map<Uuid, Action>,
    candidates: List<Action>,
    emptyText: String,
    onChange: (List<Uuid>) -> Unit,
) {
    if (ids.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ids.forEachIndexed { index, id ->
        val action = pool[id]
        ListItem(
            leadingContent = action?.let { groupIndicator(it) },
            headlineContent = { Text(action?.name?.ifBlank { "(unnamed)" } ?: "(missing action)") },
            trailingContent = {
                Row {
                    IconButton(onClick = { onChange(ids.moveItem(index, index - 1)) }, enabled = index > 0) {
                        Icon(painterResource(Res.drawable.keyboard_double_arrow_up), contentDescription = "Move up")
                    }
                    IconButton(
                        onClick = { onChange(ids.moveItem(index, index + 1)) },
                        enabled = index < ids.lastIndex,
                    ) {
                        Icon(painterResource(Res.drawable.keyboard_double_arrow_down), contentDescription = "Move down")
                    }
                    IconButton(onClick = { onChange(ids.filterIndexed { i, _ -> i != index }) }) {
                        Icon(painterResource(Res.drawable.close), contentDescription = "Remove")
                    }
                }
            },
        )
    }
    ActionPickerButton(candidates = candidates, onPick = { onChange(ids + it) })
}

@Composable
private fun ActionPickerButton(
    candidates: List<Action>,
    onPick: (Uuid) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = candidates.isNotEmpty()) {
            Icon(painterResource(Res.drawable.add), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.name.ifBlank { "(unnamed)" }) },
                    onClick = {
                        expanded = false
                        onPick(action.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun EditActionDialog(
    action: Action,
    pool: List<Action>,
    onSave: (Action) -> Unit,
    onClose: () -> Unit,
) {
    val isGroup = action.isGroup
    val name = rememberTextFieldState(action.name)
    val script = rememberTextFieldState(action.script ?: "")
    var childIds by remember { mutableStateOf(action.children) }
    val poolById = remember(pool) { pool.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isGroup) "Edit group" else "Edit action") },
        confirmButton = {
            TextButton(
                enabled = name.text.isNotBlank(),
                onClick = {
                    onSave(
                        action.copy(
                            name = name.text.toString(),
                            script = if (isGroup) null else script.text.toString(),
                            children = if (isGroup) childIds else emptyList(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    state = name,
                    label = { Text("Name") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isGroup) {
                    Text("Actions in this group", style = MaterialTheme.typography.titleSmall)
                    ActionIdListEditor(
                        ids = childIds,
                        pool = poolById,
                        candidates = childCandidates(action.id, pool).filterNot { it.id in childIds },
                        emptyText = "No actions in this group yet.",
                        onChange = { childIds = it },
                    )
                } else {
                    TextField(
                        state = script,
                        label = { Text("Script (WSL)") },
                        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 10),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}
