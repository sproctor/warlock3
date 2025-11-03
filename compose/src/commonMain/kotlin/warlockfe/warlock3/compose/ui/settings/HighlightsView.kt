package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.add
import warlockfe.warlock3.compose.generated.resources.audio_file
import warlockfe.warlock3.compose.generated.resources.delete
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.generated.resources.palette
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.uuid.Uuid

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun HighlightsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    highlightRepository: HighlightRepositoryImpl,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id
    val highlights by if (currentCharacterId == null) {
        highlightRepository.observeGlobal()
    } else {
        highlightRepository.observeByCharacter(currentCharacterId)
    }
        .collectAsState(emptyList())
    var editingHighlight by remember { mutableStateOf<Highlight?>(null) }

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Highlights", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        ScrollableColumn(
            Modifier.fillMaxWidth().weight(1f)
        ) {
            highlights.forEach { highlight ->
                ListItem(
                    headlineContent = {
                        Text(text = highlight.pattern)
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editingHighlight = highlight }
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.edit),
                                    contentDescription = "Edit",
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    GlobalScope.launch { highlightRepository.deleteById(highlight.id) }
                                }
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.delete),
                                    contentDescription = "Delete",
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
            IconButton(onClick = {
                editingHighlight = Highlight(
                    id = Uuid.random(),
                    pattern = "",
                    styles = emptyMap(),
                    isRegex = false,
                    ignoreCase = true,
                    matchPartialWord = true,
                    sound = null,
                )
            }) {
                Icon(painter = painterResource(Res.drawable.add), contentDescription = null)
            }
        }
    }
    val scope = rememberCoroutineScope()
    editingHighlight?.let { highlight ->
        EditHighlightDialog(
            highlight = highlight,
            saveHighlight = { newHighlight ->
                scope.launch {
                    if (currentCharacterId != null) {
                        highlightRepository.save(currentCharacterId, newHighlight)
                    } else {
                        highlightRepository.saveGlobal(newHighlight)
                    }
                    editingHighlight = null
                }
            },
            onClose = { editingHighlight = null }
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
    val styles = remember { mutableStateListOf<StyleDefinition>().apply { addAll(highlight.styles.values) } }
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
                            styles = styles.mapIndexed { index, style -> Pair(index, style) }.toMap(),
                            isRegex = isRegex,
                            matchPartialWord = matchPartialWord,
                            ignoreCase = ignoreCase,
                            sound = sound.text.toString().ifBlank { null },
                        )
                    )
                }
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
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row {
                    Row(Modifier.clickable { isRegex = false }) {
                        RadioButton(
                            selected = !isRegex,
                            onClick = { isRegex = false },
                        )
                        Text(
                            text = "Text highlight",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Row(Modifier.clickable { isRegex = true }) {
                        RadioButton(
                            selected = isRegex,
                            onClick = { isRegex = true }
                        )
                        Text(
                            text = "Regex highlight",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                var error: String? = null
                // Add | to match empty string, then match and see how many groups there are
                val groupCount = if (isRegex) {
                    try {
                        Regex("$pattern|").find("")?.groups?.size ?: 1
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
                    }
                )

                if (styles.size < groupCount + 1) {
                    for (i in styles.size..groupCount) {
                        styles.add(StyleDefinition())
                    }
                }
                ScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (i in 0 until groupCount) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val style = styles[i]
                            if (isRegex) {
                                Text("$i:", Modifier.align(Alignment.CenterVertically))
                            }
                            val textColorState = rememberTextFieldState(style.textColor.toHexString() ?: "")
                            LaunchedEffect(textColorState) {
                                snapshotFlow { textColorState.text.toString() }
                                    .collectLatest {
                                        styles[i] = style.copy(
                                            textColor = it.toWarlockColor() ?: WarlockColor.Unspecified
                                        )
                                    }
                            }
                            ColorTextField(
                                modifier = Modifier.weight(1f),
                                label = "Text color",
                                state = textColorState,
                            )

                            val backgroundColorState = rememberTextFieldState(style.backgroundColor.toHexString() ?: "")
                            LaunchedEffect(backgroundColorState) {
                                snapshotFlow { backgroundColorState.text.toString() }
                                    .collectLatest {
                                        styles[i] =
                                            style.copy(
                                                backgroundColor = it.toWarlockColor()
                                                    ?: WarlockColor.Unspecified
                                            )
                                    }
                            }
                            ColorTextField(
                                modifier = Modifier.weight(1f),
                                label = "Background color",
                                state = backgroundColorState,
                            )
                        }
                    }
                }
                if (!isRegex) {
                    Row {
                        val style = styles[0]
                        Checkbox(
                            checked = style.entireLine,
                            onCheckedChange = {
                                styles[0] = style.copy(entireLine = it)
                            }
                        )
                        Text(
                            text = "Highlight entire line",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    Row {
                        Checkbox(
                            checked = matchPartialWord,
                            onCheckedChange = { matchPartialWord = it })
                        Text(
                            text = "Match partial words",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
                Row {
                    Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                    Text(
                        text = "Ignore case",
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                val soundLauncher = rememberFilePickerLauncher { file ->
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
                                contentDescription = "Select sound file"
                            )
                        }
                    }
                )
            }
        }
    )
}

@Composable
fun ColorTextField(
    modifier: Modifier,
    label: String,
    state: TextFieldState,
) {
    var editColor by remember { mutableStateOf<Pair<String, (WarlockColor) -> Unit>?>(null) }
    var invalidColor by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collectLatest {
                invalidColor = it.toWarlockColor() == null && it.isNotEmpty()
            }
    }
    OutlinedTextField(
        label = {
            Text(
                text = if (invalidColor) {
                    "Invalid color string"
                } else {
                    label
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        state = state,
        trailingIcon = {
            IconButton(
                onClick = {
                    editColor = state.text.toString() to {
                        state.setTextAndPlaceCursorAtEnd(it.toHexString() ?: "")
                    }
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.palette),
                    contentDescription = "Color picker"
                )
            }
        },
        isError = invalidColor
    )
    editColor?.let { (colorText, setColor) ->
        val initialColor = colorText.toWarlockColor()?.specifiedOrNull()?.toColor() ?: Color.Black
        ColorPickerDialog(
            initialColor = initialColor,
            onCloseRequest = { editColor = null },
            onColorSelected = { color ->
                setColor(color)
                editColor = null
            }
        )
    }
}
