package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.ConfirmationDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.util.getLabel
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.macro.MacroCommand
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.MacroRepository

// TODO: use a ViewModel to handle business logic
@OptIn(DelicateCoroutinesApi::class)
@Composable
fun MacrosView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    macroRepository: MacroRepository,
) {
    var currentCharacter by remember(initialCharacter) { mutableStateOf(initialCharacter) }
    val macros by if (currentCharacter == null) {
        macroRepository.observeGlobalMacros()
    } else {
        macroRepository.observeOnlyCharacterMacros(currentCharacter!!.id)
    }.collectAsState(emptyList())
    var editingMacro by remember { mutableStateOf<EditMacroState>(EditMacroState.Closed) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Macros", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        ScrollableColumn(
            Modifier.fillMaxWidth()
                .weight(1f)
        ) {
            macros.forEach { macro ->
                ListItem(
                    headlineContent = { Text(macro.keyCombo.toDisplayString()) },
                    supportingContent = { Text(macro.command) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingMacro = EditMacroState.Edit(macro) }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    GlobalScope.launch {
                                        macroRepository.delete(
                                            currentCharacter?.id ?: "global",
                                            macro.keyCombo
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete"
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
            OutlinedButton(
                onClick = { confirmReset = true }
            ) {
                Text("Reset global macros")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editingMacro = EditMacroState.Edit(null) }) {
                Text("Create macro")
            }
        }
    }
    if (confirmReset) {
        ConfirmationDialog(
            title = "Reset global macros",
            text = "Confirm that you want to delete all existing global macros and add the default macros",
            onConfirm = {
                confirmReset = false
                GlobalScope.launch {
                    macroRepository.deleteAllGlobals()
                    macroRepository.insertDefaultMacrosIfNeeded()
                }
            },
            onDismiss = {
                confirmReset = false
            }
        )
    }
    when (val state = editingMacro) {
        is EditMacroState.Edit -> {
            val macro = state.macroCommand
            val (initialKey, modifiers) = macro?.let { keyComboToKey(it.keyCombo) } ?: (null to emptySet())
            EditMacroDialog(
                key = initialKey,
                modifiers = modifiers,
                value = macro?.command ?: "",
                saveMacro = { newMacro ->
                    scope.launch {
                        val oldKeyCombo = macro?.keyCombo
                        if (oldKeyCombo != null && newMacro.keyCombo != oldKeyCombo) {
                            if (currentCharacter != null) {
                                macroRepository.delete(currentCharacter!!.id, macro.keyCombo)
                            } else {
                                macroRepository.delete("global", macro.keyCombo)
                            }
                        }
                        if (currentCharacter != null) {
                            macroRepository.put(currentCharacter!!.id, newMacro.keyCombo, newMacro.command)
                        } else {
                            macroRepository.put("global", newMacro.keyCombo, newMacro.command)
                        }
                        editingMacro = EditMacroState.Closed
                    }
                },
                onClose = { editingMacro = EditMacroState.Closed }
            )
        }
        else -> Unit
    }
}

private fun keyComboToKey(keyCombo: MacroKeyCombo): Pair<Key, Set<String>> {
    val modifiers = mutableSetOf<String>()
    if (keyCombo.ctrl) {
        modifiers.add("ctrl")
    }
    if (keyCombo.alt) {
        modifiers.add("alt")
    }
    if (keyCombo.shift) {
        modifiers.add("shift")
    }
    if (keyCombo.meta) {
        modifiers.add("meta")
    }
    return Key(keyCombo.keyCode) to modifiers
}

private fun MacroKeyCombo.toDisplayString(): String {
    val keyString = StringBuilder()
    if (ctrl) {
        keyString.append("ctrl+")
    }
    if (alt) {
        keyString.append("alt+")
    }
    if (shift) {
        keyString.append("shift+")
    }
    if (meta) {
        keyString.append("meta+")
    }
    keyString.append(Key(keyCode).getLabel())
    return keyString.toString()
}

sealed class EditMacroState {
    data object Closed : EditMacroState()
    data class Edit(val macroCommand: MacroCommand?) : EditMacroState()
}