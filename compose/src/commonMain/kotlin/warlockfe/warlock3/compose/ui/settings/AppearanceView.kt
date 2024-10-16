package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
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
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = presets["default"]?.textColor?.toColor() ?: Color.Unspecified
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
            "link",
            "roomName",
            "speech",
            "thought",
            "watching",
            "whisper",
            "echo"
        )
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
                                Modifier.size(16.dp).background(style.textColor.toColor())
                                    .border(1.dp, Color.Black)
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
                        Text("Font: ${style.fontFamily ?: "Default"} ${style.fontSize ?: "Default"}")
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

@Composable
fun FontPickerDialog(
    currentStyle: StyleDefinition,
    onCloseRequest: () -> Unit,
    onSaveClicked: (FontUpdate) -> Unit,
) {
    val initialFontSize = currentStyle.fontSize ?: MaterialTheme.typography.bodyMedium.fontSize.value
    var size by remember(initialFontSize) {
        mutableStateOf(initialFontSize.toString())
    }
    var newFontFamily by remember(currentStyle.fontFamily) {
        mutableStateOf(currentStyle.fontFamily ?: "Default")
    }
    AlertDialog(
        onDismissRequest = onCloseRequest,
        title = { Text("Choose a font") },
        confirmButton = {
            Button(onClick = {
                onSaveClicked(
                    FontUpdate(
                        size.toFloatOrNull(),
                        newFontFamily
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCloseRequest) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(value = size, onValueChange = { size = it })

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
                                .then(
                                    if (name == newFontFamily) Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(
                                            .2f
                                        )
                                    ) else Modifier
                                ),
                            leadingContent = { Text(text = "aA", fontFamily = fontFamily) },
                            headlineContent = { Text(text = name) }
                        )
                    }
                }
                Button(onClick = { onSaveClicked(FontUpdate(null, null)) }) {
                    Text("Reset to defaults")
                }
            }
        }
    )
}

data class FontUpdate(val size: Float?, val fontFamily: String?)

val fontFamilyMap = mapOf(
    "Default" to FontFamily.Default,
    "Serif" to FontFamily.Serif,
    "SansSerif" to FontFamily.SansSerif,
    "Monospace" to FontFamily.Monospace,
)