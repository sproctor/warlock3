package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockRadioButtonRow
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.ui.settings.DesktopSettingsCharacterSelector
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.export.CharacterExport
import warlockfe.warlock3.core.prefs.export.WarlockExport
import warlockfe.warlock3.core.prefs.repositories.ImportMode

private const val GLOBAL_CHARACTER_ID = "global"

/** UI-level choice for a character; [SKIP] means do not import that character at all. */
private enum class CharacterChoice { MERGE, REPLACE, SKIP }

private fun CharacterChoice.toImportMode(): ImportMode? =
    when (this) {
        CharacterChoice.MERGE -> ImportMode.MERGE
        CharacterChoice.REPLACE -> ImportMode.REPLACE
        CharacterChoice.SKIP -> null
    }

/**
 * Import a single exported character onto an existing character chosen by the user.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun ImportCharacterDialog(
    character: CharacterExport,
    characters: List<GameCharacter>,
    onImport: (targetCharacterId: String, mode: ImportMode) -> Unit,
    onCancel: () -> Unit,
) {
    var target by remember(characters) { mutableStateOf(characters.firstOrNull()) }
    var mode by remember { mutableStateOf(ImportMode.MERGE) }

    WarlockDialog(title = "Import character", onCloseRequest = onCancel, width = 480.dp, height = 340.dp) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import settings from \"${character.name}\" into which character?")
            if (characters.isEmpty()) {
                Text("There are no characters to import into. Create a character first.")
            } else {
                DesktopSettingsCharacterSelector(
                    selectedCharacter = target,
                    characters = characters,
                    onSelect = { target = it },
                )
                Column {
                    WarlockRadioButtonRow(
                        selected = mode == ImportMode.MERGE,
                        onClick = { mode = ImportMode.MERGE },
                        text = "Merge (keep existing settings, add/overwrite imported ones)",
                    )
                    WarlockRadioButtonRow(
                        selected = mode == ImportMode.REPLACE,
                        onClick = { mode = ImportMode.REPLACE },
                        text = "Replace (clear the character's settings first)",
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onCancel, text = "Cancel")
                WarlockButton(
                    onClick = { target?.let { onImport(it.id, mode) } },
                    text = "Import",
                    enabled = target != null,
                )
            }
        }
    }
}

/**
 * Restore a full backup, prompting per character (Merge / Replace / Skip).
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun ImportFullDialog(
    export: WarlockExport,
    existingCharacterIds: Set<String>,
    onImport: (resolutions: Map<String, ImportMode>) -> Unit,
    onCancel: () -> Unit,
) {
    val choices =
        remember(export) {
            mutableStateMapOf<String, CharacterChoice>().apply {
                export.characters.forEach { put(it.id, CharacterChoice.MERGE) }
            }
        }

    WarlockDialog(title = "Import all settings", onCloseRequest = onCancel, width = 600.dp, height = 640.dp) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Accounts (usernames only), connections, and global settings will be restored. " +
                    "Choose how to handle each character:",
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                export.characters.forEach { character ->
                    val isGlobal = character.id == GLOBAL_CHARACTER_ID
                    val existing = isGlobal || character.id in existingCharacterIds
                    val choice = choices[character.id] ?: CharacterChoice.MERGE
                    Column {
                        val label =
                            when {
                                isGlobal -> "Global (shared settings)"
                                existing -> "${character.gameCode} ${character.name}"
                                else -> "${character.gameCode} ${character.name} (new)"
                            }
                        Text(label)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            WarlockRadioButtonRow(
                                selected = choice == CharacterChoice.MERGE,
                                onClick = { choices[character.id] = CharacterChoice.MERGE },
                                text = if (existing) "Merge" else "Create",
                            )
                            if (existing) {
                                WarlockRadioButtonRow(
                                    selected = choice == CharacterChoice.REPLACE,
                                    onClick = { choices[character.id] = CharacterChoice.REPLACE },
                                    text = "Replace",
                                )
                            }
                            WarlockRadioButtonRow(
                                selected = choice == CharacterChoice.SKIP,
                                onClick = { choices[character.id] = CharacterChoice.SKIP },
                                text = "Skip",
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onCancel, text = "Cancel")
                WarlockButton(
                    onClick = {
                        val resolutions =
                            choices.mapNotNull { (id, choice) -> choice.toImportMode()?.let { id to it } }.toMap()
                        onImport(resolutions)
                    },
                    text = "Import",
                )
            }
        }
    }
}

/**
 * Prompts for a target character (including Global) before an action such as choosing a file.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun SelectCharacterDialog(
    title: String,
    confirmText: String,
    characters: List<GameCharacter>,
    onSelect: (GameCharacter?) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember(characters) { mutableStateOf<GameCharacter?>(null) }

    WarlockDialog(title = title, onCloseRequest = onCancel, width = 460.dp, height = 220.dp) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DesktopSettingsCharacterSelector(
                selectedCharacter = selected,
                characters = characters,
                onSelect = { selected = it },
                allowGlobal = true,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onCancel, text = "Cancel")
                WarlockButton(onClick = { onSelect(selected) }, text = confirmText)
            }
        }
    }
}

/**
 * Shows the per-line results of importing a Wrayth settings file.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun WraythImportResultDialog(
    messages: List<String>,
    onClose: () -> Unit,
) {
    WarlockDialog(title = "Import results", onCloseRequest = onClose, width = 600.dp, height = 400.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectionContainer {
                WarlockScrollableColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    messages.forEach { message ->
                        Text(message)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockButton(onClick = onClose, text = "OK")
            }
        }
    }
}
