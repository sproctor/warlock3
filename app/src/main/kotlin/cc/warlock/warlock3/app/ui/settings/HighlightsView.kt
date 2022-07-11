package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.app.WarlockIcons
import cc.warlock.warlock3.app.components.ColorPickerDialog
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.HighlightRepository
import cc.warlock.warlock3.core.prefs.PresetRepository
import cc.warlock.warlock3.core.prefs.defaultStyles
import cc.warlock.warlock3.core.prefs.models.Highlight
import cc.warlock.warlock3.core.text.*
import cc.warlock.warlock3.core.util.toWarlockColor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

@OptIn(ExperimentalMaterialApi::class, DelicateCoroutinesApi::class)
@Composable
fun HighlightsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    highlightRepository: HighlightRepository,
    presetRepository: PresetRepository,
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
    var defaultStyle by remember { mutableStateOf(defaultStyles["default"]) }
    val defaultTextColor = defaultStyle?.textColor?.specifiedOrNull()?.toColor() ?: Color.Unspecified
    val defaultBackgroundColor = defaultStyle?.backgroundColor?.toColor() ?: Color.Unspecified
    LaunchedEffect(currentCharacterId) {
        if (currentCharacterId != null) {
            presetRepository.observePresetsForCharacter(currentCharacterId).map { it["default"] }
                .collect {
                    defaultStyle = it
                }
        }
    }
    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = "Highlights", style = MaterialTheme.typography.h5)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).background(defaultBackgroundColor).clip(MaterialTheme.shapes.medium)
        ) {
            highlights.forEach { highlight ->
                val color = highlight.styles[0]?.textColor?.specifiedOrNull()?.toColor() ?: defaultTextColor
                val background = highlight.styles[0]?.backgroundColor?.toColor() ?: defaultBackgroundColor
                ListItem(
                    text = {
                        Text(text = highlight.pattern, style = TextStyle(color = color, background = background))
                    },
                    trailing = {
                        Row {
                            IconButton(
                                onClick = { editingHighlight = highlight }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = defaultTextColor
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
                                    tint = defaultTextColor
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
                    val characterId = currentCharacter?.id
                    if (characterId != null) {
                        highlightRepository.save(characterId, newHighlight)
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

    Dialog(
        state = rememberDialogState(width = 600.dp, height = 300.dp),
        onCloseRequest = onClose,
        title = "Edit Highlight"
    ) {
        Column(
            modifier = Modifier
                .scrollable(
                    state = rememberScrollState(),
                    orientation = Orientation.Horizontal
                )
                .padding(24.dp)
        ) {
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
            Column {
                for (i in 0 until groupCount) {
                    val style = styles[i]
                    Row {
                        var textColorValue by remember { mutableStateOf(style.textColor.toHexString() ?: "") }
                        ColorTextField(
                            label = "Text color",
                            value = textColorValue,
                            onValueChanged = {
                                textColorValue = it
                                it.toWarlockColor()?.let { color ->
                                    styles[i] = style.copy(textColor = color)
                                }
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
                                it.toWarlockColor()?.let { color ->
                                    styles[i] = style.copy(backgroundColor = color)
                                }
                            }
                        )
                    }
                }
                Row {
                    Checkbox(checked = matchPartialWord, onCheckedChange = { matchPartialWord = it })
                    Text("Match partial words")
                }
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
            invalidColor = it.toWarlockColor() == null
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
        val color = colorText.toWarlockColor() ?: WarlockColor.Unspecified
        val initialColor = if (color.isUnspecified()) Color.Black else color.toColor()
        ColorPickerDialog(
            initialColor = initialColor,
            onCloseRequest = { editColor = null },
            onColorSelected = { color ->
                setColor(color ?: WarlockColor.Unspecified)
                editColor = null
            }
        )
    }
}