package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ConfirmationDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.compose.util.keyComboToKey
import warlockfe.warlock3.compose.util.toDisplayString
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.macro.Macro
import warlockfe.warlock3.core.prefs.repositories.MacroRepository

@Composable
fun MacrosView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    macroRepository: MacroRepository,
    modifier: Modifier = Modifier,
) {
    var currentCharacter by remember(initialCharacter) { mutableStateOf(initialCharacter) }
    val macros by if (currentCharacter == null) {
        macroRepository.observeGlobalMacros()
    } else {
        macroRepository.observeOnlyCharacterMacros(currentCharacter!!.id)
    }.collectAsState(emptyList())
    var editingMacro by remember { mutableStateOf<EditMacroState>(EditMacroState.Closed) }
    var confirmReset by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    SettingsListScaffold(
        title = "Macros",
        selectedCharacter = currentCharacter,
        characters = characters,
        onSelectCharacter = { currentCharacter = it },
        modifier = modifier,
    ) {
        ScrollableColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            macros.forEach { macro ->
                ListItem(
                    headlineContent = { Text(macro.keyCombo.toDisplayString()) },
                    supportingContent = { Text(macro.action) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingMacro = EditMacroState.Edit(macro) },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.edit),
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        macroRepository.delete(
                                            currentCharacter?.id ?: "global",
                                            macro.keyCombo,
                                        )
                                    }
                                },
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
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalButton(
                onClick = { confirmReset = true },
            ) {
                Text("Reset global macros")
            }
            ExtendedFloatingActionButton(
                onClick = { editingMacro = EditMacroState.Edit(null) },
                icon = { Icon(painter = painterResource(Res.drawable.add), contentDescription = null) },
                text = { Text("Add macro") },
            )
        }
    }
    if (confirmReset) {
        ConfirmationDialog(
            title = "Reset global macros",
            text = "Confirm that you want to delete all existing global macros and add the default macros",
            onConfirm = {
                confirmReset = false
                coroutineScope.launch {
                    macroRepository.deleteAllGlobals()
                    macroRepository.insertDefaultMacrosIfNeeded()
                }
            },
            onDismiss = {
                confirmReset = false
            },
        )
    }
    when (val state = editingMacro) {
        is EditMacroState.Edit -> {
            val macro = state.macro
            val (initialKey, modifiers) = macro?.let { keyComboToKey(it.keyCombo) } ?: (null to emptySet())
            EditMacroDialog(
                key = initialKey,
                modifiers = modifiers,
                value = macro?.action ?: "",
                saveMacro = { newMacro ->
                    coroutineScope.launch {
                        val oldKeyCombo = macro?.keyCombo
                        if (oldKeyCombo != null && newMacro.keyCombo != oldKeyCombo) {
                            if (currentCharacter != null) {
                                macroRepository.delete(currentCharacter!!.id, macro.keyCombo)
                            } else {
                                macroRepository.delete("global", macro.keyCombo)
                            }
                        }
                        if (currentCharacter != null) {
                            macroRepository.put(currentCharacter!!.id, newMacro.keyCombo, newMacro.action)
                        } else {
                            macroRepository.put("global", newMacro.keyCombo, newMacro.action)
                        }
                        editingMacro = EditMacroState.Closed
                    }
                },
                onClose = { editingMacro = EditMacroState.Closed },
            )
        }

        else -> {
            Unit
        }
    }
}

sealed class EditMacroState {
    data object Closed : EditMacroState()

    data class Edit(
        val macro: Macro?,
    ) : EditMacroState()
}
