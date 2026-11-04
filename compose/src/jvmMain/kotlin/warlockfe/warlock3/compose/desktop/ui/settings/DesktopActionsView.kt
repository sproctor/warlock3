package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockMenuButton
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.ui.settings.ActionsViewModel
import warlockfe.warlock3.compose.ui.settings.childCandidates
import warlockfe.warlock3.compose.ui.settings.moveItem
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.Action
import kotlin.uuid.Uuid

/**
 * Desktop editor for the per-character action buttons: a toolbar section (the ordered buttons drawn
 * on the game screen) over the flat pool of all defined actions. A leaf runs a WSL script; a group
 * references other actions by id and opens a drill-down menu.
 */
@Composable
fun DesktopActionsView(
    viewModel: ActionsViewModel,
    allCharacters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val selectedCharacter by viewModel.currentCharacter.collectAsState()
    val pool by viewModel.pool.collectAsState()
    val toolbar by viewModel.toolbar.collectAsState()
    val poolById = remember(pool) { pool.associateBy { it.id } }
    var editingAction by remember { mutableStateOf<Action?>(null) }

    SettingsListScaffold(
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = viewModel::selectCharacter,
        modifier = modifier.fillMaxSize(),
    ) {
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text("Button bar")
            ActionIdListEditor(
                ids = toolbar,
                pool = poolById,
                candidates = pool.filterNot { it.id in toolbar },
                emptyText = "No buttons yet. Add an action below to the bar.",
                onChange = viewModel::setToolbar,
            )

            Spacer(Modifier.height(24.dp))
            Text("All actions")
            if (pool.isEmpty()) {
                Text("No actions defined yet.")
            }
            pool.forEach { action ->
                WarlockListItem(
                    headline = { Text(action.label()) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(onClick = { editingAction = action }, text = "Edit")
                            WarlockOutlinedButton(
                                onClick = { viewModel.delete(action) },
                                text = "Delete",
                            )
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
        ) {
            WarlockOutlinedButton(
                onClick = { editingAction = Action(id = Uuid.random(), name = "", script = null) },
                text = "New group",
            )
            WarlockButton(
                onClick = { editingAction = Action(id = Uuid.random(), name = "", script = "") },
                text = "New action",
            )
        }
    }

    editingAction?.let { action ->
        DesktopEditActionDialog(
            action = action,
            pool = pool,
            onSave = { updated ->
                viewModel.save(updated) { editingAction = null }
            },
            onClose = { editingAction = null },
        )
    }
}

private fun Action.label(): String = name.ifBlank { "(unnamed)" } + if (isGroup) "  (group)" else ""

@Composable
private fun ActionIdListEditor(
    ids: List<Uuid>,
    pool: Map<Uuid, Action>,
    candidates: List<Action>,
    emptyText: String,
    onChange: (List<Uuid>) -> Unit,
) {
    if (ids.isEmpty()) {
        Text(emptyText)
    }
    ids.forEachIndexed { index, id ->
        val action = pool[id]
        WarlockListItem(
            headline = { Text(action?.label() ?: "(missing action)") },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WarlockOutlinedButton(
                        onClick = { onChange(ids.moveItem(index, index - 1)) },
                        text = "Up",
                        enabled = index > 0,
                    )
                    WarlockOutlinedButton(
                        onClick = { onChange(ids.moveItem(index, index + 1)) },
                        text = "Down",
                        enabled = index < ids.lastIndex,
                    )
                    WarlockOutlinedButton(
                        onClick = { onChange(ids.filterIndexed { i, _ -> i != index }) },
                        text = "Remove",
                    )
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
    WarlockMenuButton(
        anchor = { toggle ->
            WarlockOutlinedButton(onClick = toggle, text = "Add", enabled = candidates.isNotEmpty())
        },
        horizontalAlignment = Alignment.Start,
    ) { dismiss ->
        candidates.forEach { action ->
            selectableItem(
                selected = false,
                onClick = {
                    dismiss()
                    onPick(action.id)
                },
            ) {
                Text(action.label())
            }
        }
    }
}

@Composable
private fun DesktopEditActionDialog(
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

    WarlockDialog(
        title = if (isGroup) "Edit group" else "Edit action",
        onCloseRequest = onClose,
        width = 520.dp,
        height = 480.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Name")
            WarlockTextField(state = name, modifier = Modifier.fillMaxWidth())
            if (isGroup) {
                Text("Actions in this group")
                WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    ActionIdListEditor(
                        ids = childIds,
                        pool = poolById,
                        candidates = childCandidates(action.id, pool).filterNot { it.id in childIds },
                        emptyText = "No actions in this group yet.",
                        onChange = { childIds = it },
                    )
                }
            } else {
                Text("Script (WSL)")
                TextArea(state = script, modifier = Modifier.fillMaxWidth().weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
                    onClick = {
                        onSave(
                            action.copy(
                                name = name.text.toString(),
                                script = if (isGroup) null else script.text.toString(),
                                children = if (isGroup) childIds else emptyList(),
                            ),
                        )
                    },
                    text = "Save",
                    enabled = name.text.isNotBlank(),
                )
            }
        }
    }
}
