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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableColumn
import warlockfe.warlock3.compose.components.CheckboxRow
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.StyleChip
import warlockfe.warlock3.compose.components.TextStyleEditor
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.audio_file
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.drag_indicator
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.generated.resources.palette
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleScope
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.resolveSourced
import warlockfe.warlock3.core.text.sampleStyle
import warlockfe.warlock3.core.text.toLayer
import kotlin.uuid.Uuid

@Composable
fun HighlightsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    highlightRepository: HighlightRepositoryImpl,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id
    val highlights by if (currentCharacterId == null) {
        highlightRepository.observeGlobal()
    } else {
        highlightRepository.observeByCharacter(currentCharacterId)
    }.collectAsState(emptyList())
    var editingHighlight by remember { mutableStateOf<Highlight?>(null) }
    val coroutineScope = rememberCoroutineScope()

    SettingsListScaffold(
        title = "Highlights",
        selectedCharacter = selectedCharacter,
        characters = allCharacters,
        onSelectCharacter = { selectedCharacter = it },
        modifier = modifier.fillMaxSize(),
    ) {
        ScrollableColumn(
            Modifier.fillMaxWidth().weight(1f),
        ) {
            ReorderableColumn(
                list = highlights,
                onSettle = { from, to ->
                    coroutineScope.launch {
                        highlightRepository.move(currentCharacterId ?: GLOBAL_CHARACTER_ID, from, to)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { _, highlight, _ ->
                key(highlight.id) {
                    ReorderableItem(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = {
                                Text(text = highlight.pattern)
                            },
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(Res.drawable.drag_indicator),
                                        contentDescription = "Drag to reorder",
                                        modifier = Modifier.draggableHandle(),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    StyleChip(
                                        resolved = resolve(listOf((highlight.styles[0] ?: StyleDefinition()).toLayer())),
                                        windowBackground = SAFE_DEFAULT_STYLE.backgroundColor.toColor(),
                                    )
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { editingHighlight = highlight },
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.edit),
                                            contentDescription = "Edit",
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch { highlightRepository.deleteById(highlight.id) }
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
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    editingHighlight =
                        Highlight(
                            id = Uuid.random(),
                            pattern = "",
                            styles = emptyMap(),
                            isRegex = false,
                            ignoreCase = true,
                            matchPartialWord = true,
                            sound = null,
                        )
                },
                text = { Text("Add highlight") },
                icon = {
                    Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
                },
            )
        }
    }
    editingHighlight?.let { highlight ->
        EditHighlightDialog(
            highlight = highlight,
            saveHighlight = { newHighlight ->
                coroutineScope.launch {
                    if (currentCharacterId != null) {
                        highlightRepository.save(currentCharacterId, newHighlight)
                    } else {
                        highlightRepository.saveGlobal(newHighlight)
                    }
                    editingHighlight = null
                }
            },
            onClose = { editingHighlight = null },
        )
    }
}

@Composable
fun EditHighlightDialog(
    highlight: Highlight,
    saveHighlight: (Highlight) -> Unit,
    onClose: () -> Unit,
) {
    val pattern = rememberTextFieldState(highlight.pattern)
    val styles =
        remember { mutableStateListOf<StyleDefinition>().apply { addAll(highlight.styles.values) } }
    var isRegex by remember { mutableStateOf(highlight.isRegex) }
    var matchPartialWord by remember { mutableStateOf(highlight.matchPartialWord) }
    var ignoreCase by remember { mutableStateOf(highlight.ignoreCase) }
    val sound = rememberTextFieldState(highlight.sound ?: "")

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Highlight") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveHighlight(
                        Highlight(
                            id = highlight.id,
                            pattern = pattern.text.toString(),
                            styles =
                                styles
                                    .mapIndexed { index, style -> Pair(index, style) }
                                    .toMap(),
                            isRegex = isRegex,
                            matchPartialWord = matchPartialWord,
                            ignoreCase = ignoreCase,
                            sound = sound.text.toString().ifBlank { null },
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.selectableGroup()) {
                    Row(
                        Modifier.selectable(
                            selected = !isRegex,
                            onClick = { isRegex = false },
                            role = Role.RadioButton,
                        ),
                    ) {
                        RadioButton(
                            selected = !isRegex,
                            onClick = null,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Text highlight",
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Row(
                        Modifier.selectable(
                            selected = isRegex,
                            onClick = { isRegex = true },
                            role = Role.RadioButton,
                        ),
                    ) {
                        RadioButton(
                            selected = isRegex,
                            onClick = null,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Regex highlight",
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }

                var error: String? = null
                // Add | to match empty string, then match and see how many groups there are
                val groupCount =
                    if (isRegex) {
                        try {
                            Regex("${pattern.text}|").find("")?.groups?.size ?: 1
                        } catch (e: Exception) {
                            error = e.message ?: "Invalid regex"
                            1
                        }
                    } else {
                        1
                    }

                TextField(
                    state = pattern,
                    label = {
                        Text("Pattern")
                    },
                    isError = error != null,
                    supportingText = {
                        if (error != null) {
                            Text("Error: $error")
                        }
                    },
                )

                // Ensure a style slot exists for the whole match (index 0) plus each capture group.
                // Done in an effect rather than mutated during composition; the list only grows, which
                // matches the previous behavior.
                LaunchedEffect(groupCount) {
                    while (styles.size < groupCount + 1) {
                        styles.add(StyleDefinition())
                    }
                }
                ScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // Cap at the slots that exist; the effect above tops the list up on the next frame.
                    for (i in 0 until minOf(groupCount, styles.size)) {
                        if (isRegex) {
                            Spacer(Modifier.height(8.dp))
                            Text("Group $i")
                        }
                        val layer = styles[i].toLayer()
                        val stack = listOf(StyleScope.CHARACTER to layer)
                        TextStyleEditor(
                            sourced = resolveSourced(stack),
                            sample = sampleStyle(stack),
                            editScope = StyleScope.CHARACTER,
                            editLayer = layer,
                            onSave = { styles[i] = it.toStyleDefinition() },
                            showFont = false,
                            showMonospace = true,
                        )
                    }
                }
                if (!isRegex) {
                    styles.getOrNull(0)?.let { style ->
                        CheckboxRow(
                            checked = style.entireLine,
                            onCheckedChange = { styles[0] = style.copy(entireLine = it) },
                            text = "Highlight entire line",
                        )
                    }
                    CheckboxRow(
                        checked = matchPartialWord,
                        onCheckedChange = { matchPartialWord = it },
                        text = "Match partial words",
                    )
                }
                CheckboxRow(
                    checked = ignoreCase,
                    onCheckedChange = { ignoreCase = it },
                    text = "Ignore case",
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
