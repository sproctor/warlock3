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
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.macros.reverseKeyMappings
import warlockfe.warlock3.compose.ui.components.ConfirmationDialog
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.client.GameCharacter
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
    var editingMacro by remember { mutableStateOf<Pair<String?, String>?>(null) }
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
                val text = macro.first
                ListItem(
                    headlineContent = { Text(text) },
                    supportingContent = { Text(macro.second) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingMacro = macro }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    GlobalScope.launch {
                                        macroRepository.delete(
                                            currentCharacter?.id ?: "global",
                                            macro.first
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
                onClick = { confirmReset = true}
            ) {
                Text("Reset global macros")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { editingMacro = Pair(null, "") }) {
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
    editingMacro?.let { macro ->
        val (initialKey, modifiers) = macro.first?.let { stringToKey(it) } ?: Pair(null, emptySet())
        EditMacroDialog(
            key = initialKey,
            modifiers = modifiers,
            value = macro.second,
            saveMacro = { key, value ->
                scope.launch {
                    if (key != macro.first) {
                        macro.first?.let {
                            if (currentCharacter != null) {
                                macroRepository.delete(currentCharacter!!.id, it)
                            } else {
                                macroRepository.deleteGlobal(it)
                            }
                        }
                    }
                    if (currentCharacter != null) {
                        macroRepository.put(currentCharacter!!.id, key, value)
                    } else {
                        macroRepository.putGlobal(key, value)
                    }
                    editingMacro = null
                }
            },
            onClose = { editingMacro = null }
        )
    }
}

fun stringToKey(value: String): Pair<Key, Set<String>> {
    val modifiers = mutableSetOf<String>()
    val parts = value.split("+")
    for (i in 0..(parts.size - 2)) {
        modifiers.add(parts[i])
    }
    val key = reverseKeyMappings[parts.last()] ?: Key(0)
    return key to modifiers
}