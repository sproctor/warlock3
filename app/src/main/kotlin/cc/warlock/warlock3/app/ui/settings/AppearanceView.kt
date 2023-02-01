package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import cc.warlock.warlock3.app.components.ColorPickerDialog
import cc.warlock.warlock3.app.util.defaultFontSize
import cc.warlock.warlock3.app.util.getEntireLineStyles
import cc.warlock.warlock3.app.util.toAnnotatedString
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.core.client.GameCharacter
import cc.warlock.warlock3.core.prefs.PresetRepository
import cc.warlock.warlock3.core.text.*
import cc.warlock.warlock3.core.window.StreamLine
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AppearanceView(
    presetRepository: PresetRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) { mutableStateOf(initialCharacter ?: characters.firstOrNull()) }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created")
        return
    }
    val presetFlow = remember(currentCharacter.id) { presetRepository.observePresetsForCharacter(currentCharacter.id) }
    val presets by presetFlow.collectAsState(emptyMap())
    val previewLines = listOf(
        StreamLine(
            text = StyledString("[Riverhaven, Crescent Way]", style = WarlockStyle.RoomName),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString(
                "This is the room description for some room in Riverhaven. It didn't exist in our old preview, so we're putting arbitrary text here.",
                style = WarlockStyle("roomdescription")
            ),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString("You also see a ") + StyledString(
                "Sir Robyn",
                style = WarlockStyle.Bold
            ) + StyledString("."),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString("say Hello", style = WarlockStyle.Command),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString("You say", style = WarlockStyle.Speech) + StyledString(", \"Hello.\""),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString("Someone whispers", style = WarlockStyle.Whisper) + StyledString(", \"Hi\""),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString("Your mind hears Someone thinking, \"hello everyone\"", style = WarlockStyle.Thought),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString(
                " __      __              .__                 __    \n" +
                        "/  \\    /  \\_____ _______|  |   ____   ____ |  | __\n" +
                        "\\   \\/\\/   /\\__  \\\\_  __ \\  |  /  _ \\_/ ___\\|  |/ /\n" +
                        " \\        /  / __ \\|  | \\/  |_(  <_> )  \\___|    < \n" +
                        "  \\__/\\  /  (____  /__|  |____/\\____/ \\___  >__|_ \\\n" +
                        "       \\/        \\/                       \\/     \\/",
                style = WarlockStyle.Mono
            ),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        )
    )

    Column(Modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
        )
        Spacer(Modifier.height(16.dp))
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = presets["default"]?.textColor?.toColor() ?: Color.Unspecified
            )
        ) {
            Box(Modifier.weight(1f)) {
                val scrollbarStyle = LocalScrollbarStyle.current
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .background(presets["default"]?.backgroundColor?.toColor() ?: Color.Unspecified)
                        .padding(end = scrollbarStyle.thickness + 1.dp)
                        .verticalScroll(scrollState)
                        .fillMaxWidth(),
                ) {
                    previewLines.forEach { line ->
                        val lineStyle = flattenStyles(
                            line.text.getEntireLineStyles(
                                variables = emptyMap(),
                                styleMap = presets,
                            )
                        )
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .background(lineStyle?.backgroundColor?.toColor() ?: Color.Unspecified)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(text = line.text.toAnnotatedString(variables = emptyMap(), styleMap = presets))
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState),
                    style = scrollbarStyle.copy(
                        hoverColor = MaterialTheme.colors.primary,
                        unhoverColor = MaterialTheme.colors.primary.copy(alpha = 0.42f)
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PresetSettings(
            styleMap = presets,
            saveStyle = { name, styleDefinition ->
                GlobalScope.launch {
                    presetRepository.save(currentCharacter.id, name, styleDefinition)
                }
            },
        )
    }
}

@Composable
fun ColumnScope.PresetSettings(
    styleMap: Map<String, StyleDefinition>,
    saveStyle: (name: String, StyleDefinition) -> Unit,
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }
    var editFont by remember { mutableStateOf<Pair<StyleDefinition, (FontUpdate) -> Unit>?>(null) }

    if (editColor != null) {
        ColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelected = { color ->
                editColor?.second?.invoke(color)
                editColor = null
            }
        )
    }
    if (editFont != null) {
        FontPickerDialog(
            currentStyle = editFont!!.first,
            onCloseRequest = { editFont = null },
            onSaveClicked = { fontUpdate ->
                editFont?.second?.invoke(fontUpdate)
                editFont = null
            }
        )
    }
    Box(Modifier.weight(1f)) {
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .verticalScroll(scrollState)
                .padding(end = LocalScrollbarStyle.current.thickness)
        ) {
            val presets = listOf("default", "bold", "command", "link", "roomName", "speech", "thought", "watching", "whisper", "echo")
            presets.forEach { preset ->
                val style = styleMap[preset]
                if (style != null) {
                    Row {
                        Text(
                            modifier = Modifier.width(160.dp).align(Alignment.CenterVertically),
                            text = preset.replaceFirstChar { it.uppercase() },
                        )
                        OutlinedButton(
                            onClick = {
                                editColor = Pair(style.textColor) { color ->
                                    saveStyle(
                                        preset,
                                        style.copy(textColor = color)
                                    )
                                }
                            }
                        ) {
                            Row {
                                Text("Content: ")
                                Box(
                                    Modifier.size(16.dp).background(style.textColor.toColor()).border(1.dp, Color.Black)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = {
                                editColor = Pair(style.backgroundColor) { color ->
                                    saveStyle(
                                        preset,
                                        style.copy(backgroundColor = color)
                                    )
                                }
                            }
                        ) {
                            Row {
                                Text("Background: ")
                                Box(
                                    Modifier.size(16.dp).background(style.backgroundColor.toColor())
                                        .border(1.dp, Color.Black)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = {
                                editFont = Pair(style) { fontUpdate ->
                                    saveStyle(preset, style.copy(fontFamily = fontUpdate.fontFamily, fontSize = fontUpdate.size))
                                }
                            }
                        ) {
                            Text("Font: ${style.fontFamily ?: "Default"} ${style.fontSize ?: "Default"}")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FontPickerDialog(
    currentStyle: StyleDefinition,
    onCloseRequest: () -> Unit,
    onSaveClicked: (FontUpdate) -> Unit,
) {
    Dialog(
        onCloseRequest = onCloseRequest,
        title = "Choose a font",
        state = rememberDialogState(width = 400.dp, height = 500.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxSize()) {
            var size by remember(currentStyle.fontSize) {
                mutableStateOf((currentStyle.fontSize ?: defaultFontSize.value).toString())
            }
            OutlinedTextField(value = size, onValueChange = { size = it })
            var newFontFamily by remember(currentStyle.fontFamily) {
                mutableStateOf(currentStyle.fontFamily ?: "Default")
            }
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .fillMaxWidth()
            ) {
                fontFamilyMap.forEach { (name, fontFamily) ->
                    ListItem(
                        modifier = Modifier
                            .clickable { newFontFamily = name }
                            .then(if (name == newFontFamily) Modifier.background(MaterialTheme.colors.primary.copy(.2f)) else Modifier),
                        icon = { Text(text = "aA", fontFamily = fontFamily) },
                        text = { Text(text = name) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onSaveClicked(FontUpdate(null, null)) }) {
                    Text("Reset to defaults")
                }
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = onCloseRequest) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = { onSaveClicked(FontUpdate(size.toDoubleOrNull(), newFontFamily)) }) {
                    Text("Save")
                }
            }
        }
    }
}

data class FontUpdate(val size: Double?, val fontFamily: String?)

val fontFamilyMap = mapOf(
    "Default" to FontFamily.Default,
    "Serif" to FontFamily.Serif,
    "SansSerif" to FontFamily.SansSerif,
    "Monospace" to FontFamily.Monospace,
)