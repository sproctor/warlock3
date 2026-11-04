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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.ui.settings.MacrosViewModel
import warlockfe.warlock3.compose.util.keyComboToKey
import warlockfe.warlock3.compose.util.toDisplayString
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.macro.Macro

@Composable
fun DesktopMacrosView(
    viewModel: MacrosViewModel,
    characters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val currentCharacter by viewModel.currentCharacter.collectAsState()
    val macros by viewModel.macros.collectAsState()
    var editingMacro by remember { mutableStateOf<DesktopEditMacroState>(DesktopEditMacroState.Closed) }
    var confirmReset by remember { mutableStateOf(false) }

    SettingsListScaffold(
        selectedCharacter = currentCharacter,
        characters = characters,
        onSelectCharacter = viewModel::selectCharacter,
        modifier = modifier.fillMaxSize(),
    ) {
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
                                onClick = { viewModel.delete(macro) },
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
                viewModel.resetGlobalMacros()
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
                    viewModel.save(macro, newMacro) { editingMacro = DesktopEditMacroState.Closed }
                },
                onClose = { editingMacro = DesktopEditMacroState.Closed },
            )
        }

        else -> {}
    }
}

sealed class DesktopEditMacroState {
    data object Closed : DesktopEditMacroState()

    data class Edit(
        val macro: Macro?,
    ) : DesktopEditMacroState()
}
