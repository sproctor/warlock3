package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.StyleChip
import warlockfe.warlock3.compose.components.TextStyleEditor
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.audio_file
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.generated.resources.palette
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.text.StyleScope
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.resolveSourced
import warlockfe.warlock3.core.text.sampleStyle
import kotlin.uuid.Uuid

@Composable
fun NamesView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    nameRepository: NameRepositoryImpl,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id
    val names by if (currentCharacterId == null) {
        nameRepository.observeGlobal()
    } else {
        nameRepository.observeByCharacter(currentCharacterId)
    }.collectAsState(emptyList())
    var editingName by remember { mutableStateOf<NameConfig?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val editingCharacterId = currentCharacterId ?: "global"

    SettingsListScaffold(
        title = "Names",
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = { selectedCharacter = it },
        modifier = modifier.fillMaxSize(),
    ) {
        ScrollableColumn(
            Modifier.fillMaxWidth().weight(1f),
        ) {
            names.forEach { name ->
                ListItem(
                    headlineContent = {
                        Text(text = name.text)
                    },
                    leadingContent = {
                        StyleChip(
                            resolved = resolve(listOf(name.toStyleLayer())),
                            windowBackground = SAFE_DEFAULT_STYLE.backgroundColor.toColor(),
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingName = name },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.edit),
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    coroutineScope.launch { nameRepository.deleteByText(editingCharacterId, name.text) }
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
            horizontalArrangement = Arrangement.End,
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingName =
                        NameConfig(
                            id = Uuid.random().toString(),
                            text = "",
                            textColor = WarlockColor.Unspecified,
                            backgroundColor = WarlockColor.Unspecified,
                            bold = false,
                            italic = false,
                            underline = false,
                            sound = null,
                        )
                },
                icon = { Icon(painter = painterResource(Res.drawable.add), contentDescription = null) },
                text = { Text("New name") },
            )
        }
    }
    editingName?.let { name ->
        EditNameDialog(
            name = name,
            saveName = { newName ->
                coroutineScope.launch {
                    nameRepository.save(editingCharacterId, newName)
                    editingName = null
                }
            },
            onClose = { editingName = null },
        )
    }
}

@Composable
fun EditNameDialog(
    name: NameConfig,
    saveName: (NameConfig) -> Unit,
    onClose: () -> Unit,
) {
    val text = rememberTextFieldState(name.text)
    val sound = rememberTextFieldState(name.sound ?: "")
    var styleLayer by remember { mutableStateOf(name.toStyleLayer()) }
    val stack = listOf(StyleScope.CHARACTER to styleLayer)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit name") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveName(
                        name
                            .withStyle(styleLayer)
                            .copy(
                                text = text.text.toString(),
                                sound = sound.text.toString().ifBlank { null },
                            ),
                    )
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val hasLowercase = text.text.firstOrNull()?.isLowerCase() == true
                TextField(
                    state = text,
                    label = {
                        Text("Name")
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    supportingText = {
                        if (hasLowercase) {
                            Text("First letter of name is lowercase")
                        }
                    },
                )
                TextStyleEditor(
                    sourced = resolveSourced(stack),
                    sample = sampleStyle(stack),
                    editScope = StyleScope.CHARACTER,
                    editLayer = styleLayer,
                    onSave = { styleLayer = it },
                    showFont = false,
                )
                val soundLauncher =
                    rememberFilePickerLauncher { file ->
                        if (file != null) {
                            sound.setTextAndPlaceCursorAtEnd(file.absolutePath())
                        }
                    }
                TextField(
                    state = sound,
                    label = { Text("Sound file") },
                    trailingIcon = {
                        IconButton(onClick = { soundLauncher.launch() }) {
                            Icon(
                                painter = painterResource(Res.drawable.audio_file),
                                contentDescription = "Select sound file",
                            )
                        }
                    },
                )
            }
        },
    )
}
