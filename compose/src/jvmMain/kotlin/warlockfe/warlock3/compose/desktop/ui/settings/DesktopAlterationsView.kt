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
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.AlterationEntity
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import kotlin.uuid.Uuid

@Composable
fun DesktopAlterationsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    alterationRepository: AlterationRepository,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id ?: "global"
    val alterations by alterationRepository
        .observeByCharacter(currentCharacterId)
        .collectAsState(emptyList())
    var editingAlteration by remember { mutableStateOf<AlterationEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text("Alterations")
        Spacer(Modifier.height(8.dp))
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            alterations.forEach { alteration ->
                WarlockListItem(
                    headline = { Text(alteration.pattern) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingAlteration = alteration },
                                text = "Edit",
                            )
                            WarlockOutlinedButton(
                                onClick = {
                                    scope.launch { alterationRepository.deleteById(alteration.id) }
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
                    editingAlteration =
                        AlterationEntity(
                            id = Uuid.random(),
                            characterId = currentCharacterId,
                            pattern = "",
                            sourceStream = null,
                            destinationStream = null,
                            result = null,
                            ignoreCase = true,
                            keepOriginal = false,
                        )
                },
                text = "New alteration",
            )
        }
    }
    editingAlteration?.let { alteration ->
        DesktopEditAlterationDialog(
            alteration = alteration,
            saveAlteration = { newAlteration ->
                scope.launch {
                    alterationRepository.save(newAlteration)
                    editingAlteration = null
                }
            },
            onClose = { editingAlteration = null },
        )
    }
}

@Composable
private fun DesktopEditAlterationDialog(
    alteration: AlterationEntity,
    saveAlteration: (AlterationEntity) -> Unit,
    onClose: () -> Unit,
) {
    val pattern = rememberTextFieldState(alteration.pattern)
    val sourceStream = rememberTextFieldState(alteration.sourceStream ?: "")
    val replacement = rememberTextFieldState(alteration.result ?: "")
    var ignoreCase by remember { mutableStateOf(alteration.ignoreCase) }
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
        title = "Edit Alteration",
        onCloseRequest = onClose,
        width = 500.dp,
        height = 400.dp,
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
            Text("Apply alteration to stream (leave blank for any)")
            WarlockTextField(state = sourceStream, modifier = Modifier.fillMaxWidth())
            WarlockCheckboxRow(
                checked = ignoreCase,
                onCheckedChange = { ignoreCase = it },
                text = "Ignore case",
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
                    onClick = {
                        saveAlteration(
                            AlterationEntity(
                                id = alteration.id,
                                characterId = alteration.characterId,
                                pattern = pattern.text.toString(),
                                sourceStream = sourceStream.text.toString().ifBlank { null },
                                destinationStream = null,
                                result = replacement.text.toString().ifBlank { null },
                                ignoreCase = ignoreCase,
                                keepOriginal = false,
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
