package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.LocalScrollbarStyle
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.ScrollbarStyle
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.FontUpdate
import warlockfe.warlock3.compose.util.getEntireLineStyles
import warlockfe.warlock3.compose.util.toAnnotatedString
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.PresetRepository
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.flattenStyles
import warlockfe.warlock3.core.window.StreamLine

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun AppearanceView(
    presetRepository: PresetRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) {
            mutableStateOf(
                initialCharacter ?: characters.firstOrNull()
            )
        }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created")
        return
    }
    val presetFlow =
        remember(currentCharacter.id) { presetRepository.observePresetsForCharacter(currentCharacter.id) }
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
            text = StyledString(
                "You say",
                style = WarlockStyle.Speech
            ) + StyledString(", \"Hello.\""),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString(
                "Someone whispers",
                style = WarlockStyle.Whisper
            ) + StyledString(", \"Hi\""),
            ignoreWhenBlank = false,
            serialNumber = 0L,
        ),
        StreamLine(
            text = StyledString(
                "Your mind hears Someone thinking, \"hello everyone\"",
                style = WarlockStyle.Thought
            ),
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
        val barColor = presets["default"]?.textColor?.toColor() ?: MaterialTheme.colorScheme.onSurface
        CompositionLocalProvider(
            LocalContentColor provides (presets["default"]?.textColor?.toColor() ?: LocalContentColor.current),
            LocalScrollbarStyle provides ScrollbarStyle(
                hoverColor = barColor.copy(alpha = 0.5f),
                unhoverColor = barColor.copy(alpha = 0.12f)
            ),
        ) {
            ScrollableColumn(
                Modifier.weight(1f)
                    .background(
                        presets["default"]?.backgroundColor?.toColor() ?: Color.Unspecified
                    )
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
                            .background(
                                lineStyle?.backgroundColor?.toColor() ?: Color.Unspecified
                            )
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = line.text.toAnnotatedString(
                                variables = emptyMap(),
                                styleMap = presets,
                                actionHandler = { },
                            )
                        )
                    }
                }
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
    ScrollableColumn(Modifier.weight(1f)) {
        val presets = listOf(
            "default",
            "bold",
            "command",
            "echo",
            "error",
            "link",
            "mono",
            "roomName",
            "speech",
            "thought",
            "watching",
            "whisper",
        )
        presets.forEach { preset ->
            val style = styleMap[preset]
            if (style != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier.width(120.dp).align(Alignment.CenterVertically),
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
                            Text("Content: ", maxLines = 1)
                            Box(
                                Modifier.size(16.dp).background(style.textColor.toColor())
                                    .border(1.dp, Color.Black)
                            )
                        }
                    }

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
                            Text("Background: ", maxLines = 1)
                            Box(
                                Modifier.size(16.dp).background(style.backgroundColor.toColor())
                                    .border(1.dp, Color.Black)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            editFont = Pair(style) { fontUpdate ->
                                saveStyle(
                                    preset,
                                    style.copy(
                                        fontFamily = fontUpdate.fontFamily,
                                        fontSize = fontUpdate.size
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Font: ${style.fontFamily ?: "Default"} ${style.fontSize ?: "Default"}", maxLines = 1)
                    }
                }
            }
        }
    }
}
