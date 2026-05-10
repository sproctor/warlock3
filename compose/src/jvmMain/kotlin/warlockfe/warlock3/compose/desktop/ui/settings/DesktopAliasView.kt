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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.AliasEntity
import warlockfe.warlock3.core.prefs.repositories.AliasRepository
import kotlin.uuid.Uuid

@Composable
fun DesktopAliasView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    aliasRepository: AliasRepository,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id ?: "global"
    val aliases by aliasRepository
        .observeByCharacter(currentCharacterId)
        .collectAsState(emptyList())
    var editingAlias by remember { mutableStateOf<AliasEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text("Aliases")
        Spacer(Modifier.height(8.dp))
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            aliases.forEach { alias ->
                WarlockListItem(
                    headline = { Text(alias.pattern) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingAlias = alias },
                                text = "Edit",
                            )
                            WarlockOutlinedButton(
                                onClick = {
                                    scope.launch { aliasRepository.deleteById(alias.id) }
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
                    editingAlias =
                        AliasEntity(
                            id = Uuid.random(),
                            characterId = currentCharacterId,
                            pattern = "",
                            replacement = "",
                        )
                },
                text = "New alias",
            )
        }
    }
    editingAlias?.let { alias ->
        DesktopEditAliasDialog(
            alias = alias,
            saveAlias = { newAlias ->
                scope.launch {
                    aliasRepository.save(newAlias)
                    editingAlias = null
                }
            },
            onClose = { editingAlias = null },
        )
    }
}

@Composable
private fun DesktopEditAliasDialog(
    alias: AliasEntity,
    saveAlias: (AliasEntity) -> Unit,
    onClose: () -> Unit,
) {
    val pattern = rememberTextFieldState(alias.pattern)
    val replacement = rememberTextFieldState(alias.replacement)
    var patternError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pattern) {
        snapshotFlow { pattern.text.toString() }
            .collectLatest {
                patternError =
                    try {
                        Regex(it)
                        null
                    } catch (e: Exception) {
                        e.message
                    }
            }
    }

    WarlockDialog(
        title = "Edit Alias",
        onCloseRequest = onClose,
        width = 460.dp,
        height = 320.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pattern")
            WarlockTextField(state = pattern, modifier = Modifier.fillMaxWidth())
            patternError?.let { Text(it) }
            Text("Replacement")
            WarlockTextField(state = replacement, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
                    onClick = {
                        saveAlias(
                            AliasEntity(
                                id = alias.id,
                                characterId = alias.characterId,
                                pattern = pattern.text.toString(),
                                replacement = replacement.text.toString(),
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
