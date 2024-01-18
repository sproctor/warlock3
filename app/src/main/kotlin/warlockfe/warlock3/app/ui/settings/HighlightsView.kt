package warlockfe.warlock3.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import warlockfe.warlock3.app.components.ColorPickerDialog
import warlockfe.warlock3.app.ui.theme.WarlockIcons
import warlockfe.warlock3.app.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.HighlightRepository
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
                Icon(imageVector = WarlockIcons.Add, contentDescription = null)
            }
        }
    }
    editingHighlight?.let { highlight ->
        EditHighlightDialog(
            highlight = highlight,
            saveHighlight = { newHighlight ->
                runBlocking {
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
    val styles = remember { mutableListOf<StyleDefinition>().apply { addAll(highlight.styles.values) } }
    var isRegex by remember { mutableStateOf(highlight.isRegex) }
    var matchPartialWord by remember { mutableStateOf(highlight.matchPartialWord) }
    var ignoreCase by remember { mutableStateOf(highlight.ignoreCase) }

    DialogWindow(
        state = rememberDialogState(width = 640.dp, height = 480.dp),
        onCloseRequest = onClose,
        title = "Edit Highlight"
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row {
                Row(Modifier.clickable { isRegex = false }) {
                    RadioButton(
                        selected = !isRegex,
                        onClick = { isRegex = false },
                    )
                    Text(text = "Text highlight", modifier = Modifier.align(Alignment.CenterVertically))
                }
                Spacer(Modifier.width(16.dp))
                Row(Modifier.clickable { isRegex = true }) {
                    RadioButton(
                        selected = isRegex,
                        onClick = { isRegex = true }
                    )
                    Text(text = "Regex highlight", modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            TextField(value = pattern, label = { Text("Pattern") }, onValueChange = { pattern = it })
            // Add | to match empty string, then match and see how many groups there are
            val groupCount = if (isRegex) try {
                Regex("$pattern|")
            } catch (e: Throwable) {
                null
            }?.find("")?.groups?.size ?: 1 else 1
            if (styles.size < groupCount + 1) {
                for (i in styles.size..groupCount) {
                    styles.add(StyleDefinition())
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val showScrollbar = groupCount > 1
                val scrollState = rememberScrollState()
                Column(
                    modifier = if (showScrollbar) Modifier.verticalScroll(scrollState).padding(end = LocalScrollbarStyle.current.thickness) else Modifier
                ) {
                    for (i in 0 until groupCount) {
                        val style = styles[i]
                        Row {
                            if (isRegex) {
                                Text("$i:", Modifier.align(Alignment.CenterVertically))
                                Spacer(Modifier.width(8.dp))
                            }
                            var textColorValue by remember { mutableStateOf(style.textColor.toHexString() ?: "") }
                            ColorTextField(
                                label = "Text color",
                                value = textColorValue,
                                onValueChanged = {
                                    textColorValue = it
                                    styles[i] = style.copy(textColor = it.toWarlockColor() ?: WarlockColor.Unspecified)
                                }
                            )

                            Spacer(Modifier.width(16.dp))
                            var backgroundColorValue by remember {
                                mutableStateOf(style.backgroundColor.toHexString() ?: "")
                            }
                            ColorTextField(
                                label = "Background color",
                                value = backgroundColorValue,
                                onValueChanged = {
                                    backgroundColorValue = it
                                    styles[i] =
                                        style.copy(backgroundColor = it.toWarlockColor() ?: WarlockColor.Unspecified)
                                }
                            )
                        }
                    }
                }
                if (showScrollbar) {
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }
            if (!isRegex) {
                Row {
                    Checkbox(checked = matchPartialWord, onCheckedChange = { matchPartialWord = it })
                    Text(text = "Match partial words", modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            Row {
                Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                Text(text = "Ignore case", modifier = Modifier.align(Alignment.CenterVertically))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onClose) {
                    Text("CANCEL")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        saveHighlight(
                            Highlight(
                                id = highlight.id,
                                pattern = pattern,
                                styles = styles.mapIndexed { index, style -> Pair(index, style) }.toMap(),
                                isRegex = isRegex,
                                matchPartialWord = matchPartialWord,
                                ignoreCase = ignoreCase,
                            )
                        )
                    }
                ) {
                    Text("OK")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorTextField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<String, (WarlockColor) -> Unit>?>(null) }
    var invalidColor by remember { mutableStateOf(false) }
    OutlinedTextField(
        label = {
            Text(
                if (invalidColor) {
                    "Invalid color string"
                } else {
                    label
                }
            )
        },
        modifier = Modifier.width(250.dp),
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
                    imageVector = Icons.Default.Palette,
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