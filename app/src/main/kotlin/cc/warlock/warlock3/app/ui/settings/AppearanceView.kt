package cc.warlock.warlock3.app.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cc.warlock.warlock3.app.components.ColorPickerDialog
import cc.warlock.warlock3.app.util.getEntireLineStyles
import cc.warlock.warlock3.app.util.toAnnotatedString
import cc.warlock.warlock3.app.util.toColor
import cc.warlock.warlock3.app.util.toWarlockColor
import cc.warlock.warlock3.core.prefs.models.GameCharacter
import cc.warlock.warlock3.core.prefs.PresetRepository
import cc.warlock.warlock3.core.text.*
import cc.warlock.warlock3.stormfront.StreamLine
import kotlinx.coroutines.runBlocking

@Composable
fun AppearanceView(
    presetRepository: PresetRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
) {
    val currentCharacterState =
        remember(initialCharacter) { mutableStateOf(initialCharacter ?: characters.firstOrNull()) }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("no characters created")
        return
    }
    val presetFlow = remember(currentCharacter.id) { presetRepository.observePresetsForCharacter(currentCharacter.id) }
    val presets by presetFlow.collectAsState(emptyMap())
    val previewLines = listOf(
        StreamLine(
            text = StyledString("[Riverhaven, Crescent Way]", style = WarlockStyle.RoomName),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString(
                "This is the room description for some room in Riverhaven. It didn't exist in our old preview, so we're putting arbitrary text here.",
                style = WarlockStyle("roomdescription")
            ),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("You also see a ") + StyledString(
                "Sir Robyn",
                style = WarlockStyle.Bold
            ) + StyledString("."),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("say Hello", style = WarlockStyle.Command),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("You say", style = WarlockStyle.Speech) + StyledString(", \"Hello.\""),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("Someone whispers", style = WarlockStyle.Whisper) + StyledString(", \"Hi\""),
            ignoreWhenBlank = false,
        ),
        StreamLine(
            text = StyledString("Your mind hears Someone thinking, \"hello everyone\"", style = WarlockStyle.Thought),
            ignoreWhenBlank = false,
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
                runBlocking {
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

    if (editColor != null) {
        ColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelected = { color ->
                editColor?.second?.invoke(color ?: WarlockColor.Unspecified)
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
            val presets = listOf("bold", "command", "roomName", "speech", "thought", "watching", "whisper", "echo")
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