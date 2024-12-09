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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.icons.Palette
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor
import java.util.*

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun HighlightsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    highlightRepository: HighlightRepository,
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
        Column(
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
                                    imageVector = Icons.Default.Edit,
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
                                    imageVector = Icons.Default.Delete,
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
                    id = UUID.randomUUID(),
                    pattern = "",
                    styles = emptyMap(),
                    isRegex = false,
                    ignoreCase = true,
                    matchPartialWord = true,
                )
            }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
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
    var pattern by remember { mutableStateOf(highlight.pattern) }
    val styles =
        remember { mutableListOf<StyleDefinition>().apply { addAll(highlight.styles.values) } }
    var isRegex by remember { mutableStateOf(highlight.isRegex) }
    var matchPartialWord by remember { mutableStateOf(highlight.matchPartialWord) }
    var ignoreCase by remember { mutableStateOf(highlight.ignoreCase) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Highlight") },
        confirmButton = {
            TextButton(
                onClick = {
                    saveHighlight(
                        Highlight(
                            id = highlight.id,
                            pattern = pattern,
                            styles = styles.mapIndexed { index, style -> Pair(index, style) }
                                .toMap(),
                            isRegex = isRegex,
                            matchPartialWord = matchPartialWord,
                            ignoreCase = ignoreCase,
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
                    } catch (e: Throwable) {
                        error = e.message ?: "Invalid regex"
                        1
                    }
                } else {
                    1
                }

                TextField(
                    value = pattern,
                    label = {
                        Text("Pattern")
                    },
                    onValueChange = { pattern = it },
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
                            var textColorValue by remember {
                                mutableStateOf(style.textColor.toHexString() ?: "")
                            }
                            ColorTextField(
                                modifier = Modifier.weight(1f),
                                label = "Text color",
                                value = textColorValue,
                                onValueChanged = {
                                    textColorValue = it
                                    styles[i] = style.copy(
                                        textColor = it.toWarlockColor() ?: WarlockColor.Unspecified
                                    )
                                }
                            )

                            var backgroundColorValue by remember {
                                mutableStateOf(style.backgroundColor.toHexString() ?: "")
                            }
                            ColorTextField(
                                modifier = Modifier.weight(1f),
                                label = "Background color",
                                value = backgroundColorValue,
                                onValueChanged = {
                                    backgroundColorValue = it
                                    styles[i] =
                                        style.copy(
                                            backgroundColor = it.toWarlockColor()
                                                ?: WarlockColor.Unspecified
                                        )
                                }
                            )
                        }
                    }
                }
                if (!isRegex) {
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
            }
        }
    )
}

@Composable
fun ColorTextField(
    modifier: Modifier,
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<String, (WarlockColor) -> Unit>?>(null) }
    var invalidColor by remember { mutableStateOf(false) }
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
        value = value,
        onValueChange = {
            onValueChanged(it)
            invalidColor = it.toWarlockColor() == null && it.isNotEmpty()
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    editColor = value to {
                        onValueChanged(it.toHexString() ?: "")
                    }
                }
            ) {
                Icon(
                    imageVector = Palette,
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