package warlockfe.warlock3.compose.desktop.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
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
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.components.DesktopColorTextField
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockRadioButtonRow
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.Highlight
import warlockfe.warlock3.core.prefs.repositories.HighlightRepositoryImpl
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.uuid.Uuid

@Composable
fun DesktopHighlightsView(
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
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text("Highlights")
        Spacer(Modifier.height(8.dp))
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            highlights.forEach { highlight ->
                WarlockListItem(
                    leading = {
                        val style = highlight.styles[0]
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .background(
                                        color = style?.backgroundColor?.toColor() ?: androidx.compose.ui.graphics.Color.Unspecified,
                                        shape = RoundedCornerShape(4.dp),
                                    ).border(
                                        1.dp,
                                        style?.textColor?.toColor()?.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified }
                                            ?: JewelTheme.globalColors.borders.normal,
                                        RoundedCornerShape(4.dp),
                                    ),
                        )
                    },
                    headline = { Text(highlight.pattern) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingHighlight = highlight },
                                text = "Edit",
                            )
                            WarlockOutlinedButton(
                                onClick = {
                                    scope.launch { highlightRepository.deleteById(highlight.id) }
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
                text = "Add highlight",
            )
        }
    }
    editingHighlight?.let { highlight ->
        DesktopEditHighlightDialog(
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
            onClose = { editingHighlight = null },
        )
    }
}

@Composable
private fun DesktopEditHighlightDialog(
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
    var patternError by remember { mutableStateOf<String?>(null) }

    WarlockDialog(
        title = "Edit Highlight",
        onCloseRequest = onClose,
        width = 700.dp,
        height = 600.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WarlockRadioButtonRow(
                    selected = !isRegex,
                    onClick = { isRegex = false },
                    text = "Text highlight",
                )
                WarlockRadioButtonRow(
                    selected = isRegex,
                    onClick = { isRegex = true },
                    text = "Regex highlight",
                )
            }

            val groupCount =
                if (isRegex) {
                    try {
                        val count = Regex("${pattern.text}|").find("")?.groups?.size ?: 1
                        patternError = null
                        count
                    } catch (e: Exception) {
                        patternError = e.message ?: "Invalid regex"
                        1
                    }
                } else {
                    patternError = null
                    1
                }

            Text("Pattern")
            WarlockTextField(state = pattern, modifier = Modifier.fillMaxWidth())
            patternError?.let { Text("Error: $it") }

            if (styles.size < groupCount + 1) {
                for (i in styles.size..groupCount) {
                    styles.add(StyleDefinition())
                }
            }
            WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (i in 0 until groupCount) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isRegex) {
                            Text("$i:", modifier = Modifier.align(Alignment.CenterVertically))
                        }
                        val textColorState =
                            rememberTextFieldState(styles[i].textColor.toHexString() ?: "")
                        LaunchedEffect(textColorState) {
                            snapshotFlow { textColorState.text.toString() }
                                .collectLatest {
                                    styles[i] =
                                        styles[i].copy(
                                            textColor = it.toWarlockColor() ?: WarlockColor.Unspecified,
                                        )
                                }
                        }
                        DesktopColorTextField(
                            modifier = Modifier.weight(1f),
                            label = "Text color",
                            state = textColorState,
                        )

                        val backgroundColorState =
                            rememberTextFieldState(styles[i].backgroundColor.toHexString() ?: "")
                        LaunchedEffect(backgroundColorState) {
                            snapshotFlow { backgroundColorState.text.toString() }
                                .collectLatest {
                                    styles[i] =
                                        styles[i].copy(
                                            backgroundColor = it.toWarlockColor() ?: WarlockColor.Unspecified,
                                        )
                                }
                        }
                        DesktopColorTextField(
                            modifier = Modifier.weight(1f),
                            label = "Background color",
                            state = backgroundColorState,
                        )
                    }
                }
            }

            if (!isRegex) {
                val style = styles[0]
                WarlockCheckboxRow(
                    checked = style.entireLine,
                    onCheckedChange = { styles[0] = style.copy(entireLine = it) },
                    text = "Highlight entire line",
                )
                WarlockCheckboxRow(
                    checked = matchPartialWord,
                    onCheckedChange = { matchPartialWord = it },
                    text = "Match partial words",
                )
            }
            WarlockCheckboxRow(
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
            Text("Sound file")
            Row(verticalAlignment = Alignment.CenterVertically) {
                WarlockTextField(state = sound, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(4.dp))
                WarlockOutlinedButton(onClick = { soundLauncher.launch() }, text = "Browse")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
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
                    text = "Save",
                )
            }
        }
    }
}
