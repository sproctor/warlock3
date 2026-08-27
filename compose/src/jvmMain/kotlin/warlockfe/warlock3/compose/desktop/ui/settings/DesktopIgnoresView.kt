package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.models.Ignore
import warlockfe.warlock3.core.prefs.models.IgnoreMatchMode
import warlockfe.warlock3.core.prefs.repositories.IgnoreRepository
import kotlin.uuid.Uuid

@Composable
fun DesktopIgnoresView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    ignoreRepository: IgnoreRepository,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id ?: GLOBAL_CHARACTER_ID
    val ignores by ignoreRepository
        .observeByCharacter(currentCharacterId)
        .collectAsState(emptyList())
    var editingIgnore by remember { mutableStateOf<Ignore?>(null) }
    val scope = rememberCoroutineScope()

    SettingsListScaffold(
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = { selectedCharacter = it },
        modifier = modifier.fillMaxSize(),
    ) {
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            ignores.forEach { ignore ->
                WarlockListItem(
                    headline = {
                        Column {
                            Text(ignore.pattern)
                            Text(ignore.matchSummary())
                        }
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingIgnore = ignore },
                                text = "Edit",
                            )
                            WarlockOutlinedButton(
                                onClick = {
                                    scope.launch { ignoreRepository.deleteById(ignore.id) }
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
            horizontalArrangement = Arrangement.End,
        ) {
            WarlockButton(
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
                text = "New ignore",
            )
        }
    }
    editingIgnore?.let { ignore ->
        DesktopEditIgnoreDialog(
            ignore = ignore,
            saveIgnore = { newIgnore ->
                scope.launch {
                    ignoreRepository.save(currentCharacterId, newIgnore)
                    editingIgnore = null
                }
            },
            onClose = { editingIgnore = null },
        )
    }
}

@Composable
private fun DesktopEditIgnoreDialog(
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

    WarlockDialog(
        title = "Edit Ignore",
        onCloseRequest = onClose,
        width = 500.dp,
        height = 400.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RadioButtonRow(
                    selected = !isRegex,
                    onClick = { isRegex = false },
                    text = "Text ignore",
                )
                RadioButtonRow(
                    selected = isRegex,
                    onClick = { isRegex = true },
                    text = "Regex ignore",
                )
            }
            Text("Pattern")
            WarlockTextField(state = pattern, modifier = Modifier.fillMaxWidth())
            patternError?.let { Text("Error: $it") }
            if (!isRegex) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    matchModeOptions.forEach { (mode, label) ->
                        RadioButtonRow(
                            selected = matchMode == mode,
                            onClick = { matchMode = mode },
                            text = label,
                        )
                    }
                }
            }
            CheckboxRow(
                checked = !ignoreCase,
                onCheckedChange = { ignoreCase = !it },
                text = "Case sensitive",
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
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
                    text = "Save",
                    enabled = patternError == null && pattern.text.isNotBlank(),
                )
            }
        }
    }
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
