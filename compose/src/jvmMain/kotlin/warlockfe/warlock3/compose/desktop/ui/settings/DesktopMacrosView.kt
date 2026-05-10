package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.util.getLabel
import warlockfe.warlock3.compose.util.insertDefaultMacrosIfNeeded
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.macro.Macro
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.prefs.repositories.MacroRepository

@Composable
fun DesktopMacrosView(
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
    var editingMacro by remember { mutableStateOf<DesktopEditMacroState>(DesktopEditMacroState.Closed) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text("Macros")
        Spacer(Modifier.height(8.dp))
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            macros.forEach { macro ->
                WarlockListItem(
                    headline = {
                        Column {
                            Text(macro.keyCombo.toDisplayString())
                            Text(macro.action)
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingMacro = DesktopEditMacroState.Edit(macro) },
                                text = "Edit",
                            )
                            WarlockOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        macroRepository.delete(
                                            currentCharacter?.id ?: "global",
                                            macro.keyCombo,
                                        )
                                    }
                                },
                                text = "Delete",
                            )
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WarlockOutlinedButton(
                onClick = { confirmReset = true },
                text = "Reset global macros",
            )
            WarlockButton(
                onClick = { editingMacro = DesktopEditMacroState.Edit(null) },
                text = "Add macro",
            )
        }
    }
    if (confirmReset) {
        DesktopConfirmationDialog(
            title = "Reset global macros",
            text = "Confirm that you want to delete all existing global macros and add the default macros",
            onConfirm = {
                confirmReset = false
                scope.launch {
                    macroRepository.deleteAllGlobals()
                    macroRepository.insertDefaultMacrosIfNeeded()
                }
            },
            onDismiss = { confirmReset = false },
        )
    }
    when (val state = editingMacro) {
        is DesktopEditMacroState.Edit -> {
            val macro = state.macro
            val (initialKey, modifiers) = macro?.let { keyComboToKey(it.keyCombo) } ?: (null to emptySet())
            DesktopEditMacroDialog(
                key = initialKey,
                modifiers = modifiers,
                value = macro?.action ?: "",
                saveMacro = { newMacro ->
                    scope.launch {
                        val oldKeyCombo = macro?.keyCombo
                        val targetCharacterId = currentCharacter?.id ?: "global"
                        if (oldKeyCombo != null && newMacro.keyCombo != oldKeyCombo) {
                            macroRepository.delete(targetCharacterId, oldKeyCombo)
                        }
                        macroRepository.put(targetCharacterId, newMacro.keyCombo, newMacro.action)
                        editingMacro = DesktopEditMacroState.Closed
                    }
                },
                onClose = { editingMacro = DesktopEditMacroState.Closed },
            )
        }
        else -> Unit
    }
}

private fun keyComboToKey(keyCombo: MacroKeyCombo): Pair<Key, Set<String>> {
    val modifiers = mutableSetOf<String>()
    if (keyCombo.ctrl) modifiers.add("ctrl")
    if (keyCombo.alt) modifiers.add("alt")
    if (keyCombo.shift) modifiers.add("shift")
    if (keyCombo.meta) modifiers.add("meta")
    return Key(keyCombo.keyCode) to modifiers
}

private fun MacroKeyCombo.toDisplayString(): String {
    val keyString = StringBuilder()
    if (ctrl) keyString.append("ctrl+")
    if (alt) keyString.append("alt+")
    if (shift) keyString.append("shift+")
    if (meta) keyString.append("meta+")
    keyString.append(Key(keyCode).getLabel())
    return keyString.toString()
}

sealed class DesktopEditMacroState {
    data object Closed : DesktopEditMacroState()

    data class Edit(
        val macro: Macro?,
    ) : DesktopEditMacroState()
}
