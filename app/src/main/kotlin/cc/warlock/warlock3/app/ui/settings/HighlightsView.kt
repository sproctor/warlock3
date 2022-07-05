package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.app.WarlockIcons
import cc.warlock.warlock3.app.components.ColorPickerDialog
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.app.util.toWarlockColor
import cc.warlock.warlock3.core.prefs.HighlightRepository
import cc.warlock.warlock3.core.prefs.models.GameCharacter
import cc.warlock.warlock3.core.prefs.models.Highlight
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.WarlockColor
import cc.warlock.warlock3.core.text.isUnspecified
import kotlinx.coroutines.runBlocking
import java.util.*

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HighlightsView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    highlightRepository: HighlightRepository,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val highlights by if (currentCharacter == null) {
        highlightRepository.observeGlobal()
    } else {
        highlightRepository.observeForCharacter(currentCharacter.id)
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
        Column(Modifier.fillMaxWidth().weight(1f)) {
            highlights.forEach { highlight ->
                ListItem(
                    modifier = Modifier.clickable { editingHighlight = highlight },
                    text = { Text(highlight.pattern) },
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = {
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
    var editColor by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var pattern by remember { mutableStateOf(highlight.pattern) }
    val styles = remember { mutableListOf<StyleDefinition>().apply { addAll(highlight.styles.values) } }
    var isRegex by remember { mutableStateOf(highlight.isRegex) }
    var matchPartialWord by remember { mutableStateOf(highlight.matchPartialWord) }
    var ignoreCase by remember { mutableStateOf(highlight.ignoreCase) }

    Dialog(
        onCloseRequest = onClose,
        state = rememberDialogState(size = DpSize(width = 1200.dp, height = 500.dp))
    ) {
        Column(
            modifier = Modifier
                .scrollable(
                    state = rememberScrollState(),
                    orientation = Orientation.Horizontal
                )
        ) {
            TextField(value = pattern, label = { Text("Pattern") }, onValueChange = { pattern = it })
            // Add | to match empty string, then match and see how many groups there are
            val groupCount = if (isRegex) try {
                Regex("$pattern|")
            } catch (e: Throwable) {
                null
            }?.find("")?.groups?.size ?: 1 else 1
            Column {
                for (i in 0 until groupCount) {
                    val style = styles.getOrNull(i)
                    Row {
                        OutlinedButton({ editColor = i to true }) {
                            Row {
                                Text("Text: ")
                                style?.textColor?.let {
                                    Box(Modifier.size(16.dp).background(it.toColor()).border(1.dp, Color.Black))
                                }
                            }
                        }
                        OutlinedButton({ editColor = i to false }) {
                            Row {
                                Text("Background: ")
                                style?.backgroundColor?.let {
                                    Box(Modifier.size(16.dp).background(it.toColor()).border(1.dp, Color.Black))
                                }
                            }
                        }
                    }
                }
                Row {
                    Checkbox(checked = matchPartialWord, onCheckedChange = { matchPartialWord = it })
                    Text("Match partial words")
                }
            }
            Row {
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
                Button(onClick = onClose) {
                    Text("CANCEL")
                }
            }
        }
    }
    editColor?.let { (group, content) ->
        val currentStyle = styles.getOrNull(group) ?: StyleDefinition()
        val initialColor = (if (content) currentStyle.textColor else currentStyle.backgroundColor).let { color ->
            if (color.isUnspecified()) Color.Black else color.toColor()
        }
        ColorPickerDialog(
            initialColor = initialColor,
            onCloseRequest = { editColor = null },
            onColorSelected = { color ->
                val warlockColor = color?.toWarlockColor() ?: WarlockColor.Unspecified
                val newStyle =
                    if (content)
                        currentStyle.copy(textColor = warlockColor)
                    else
                        currentStyle.copy(backgroundColor = warlockColor)
                if (styles.size < group + 1) {
                    for (i in styles.size..group) {
                        styles.add(StyleDefinition())
                    }
                }
                styles[group] = newStyle
                editColor = null
            }
        )
    }
}
