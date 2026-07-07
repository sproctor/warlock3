package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.CheckboxRow
import warlockfe.warlock3.compose.components.ColorPickerButton
import warlockfe.warlock3.compose.components.ColorPickerDialog
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.defaultScrollbarStyle
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.ui.window.StreamTextLine
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toAnnotatedString
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.compose.util.toStyleDefinition
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle

@Composable
fun AppearanceView(
    presetRepository: PresetRepository,
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    modifier: Modifier = Modifier,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) {
            mutableStateOf(
                initialCharacter ?: characters.firstOrNull(),
            )
        }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created")
        return
    }
    val presetFlow =
        remember(currentCharacter.id) { presetRepository.observePresetsForCharacter(currentCharacter.id) }
    val savedPresets by presetFlow.collectAsState(emptyMap())
    // Show all presets: the skin's defaults (resolved for the current mode), overridden by saved ones.
    val skin = LocalSkin.current
    val isDark = LocalDarkTheme.current
    val presets = remember(skin, isDark, savedPresets) { skin.toPresets(isDark) + savedPresets }
    val defaultFont by remember(currentCharacter.id) {
        characterSettingsRepository.observeDefaultFont(currentCharacter.id)
    }.collectAsState(null)
    val monoFont by remember(currentCharacter.id) {
        characterSettingsRepository.observeMonoFont(currentCharacter.id)
    }.collectAsState(null)
    val previewLines =
        remember(presets, monoFont) {
            listOf(
                StreamTextLine(
                    text =
                        StyledString("[Riverhaven, Crescent Way]", style = WarlockStyle.RoomName)
                            .toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    entireLineStyle = WarlockStyle.RoomName.toStyleDefinition(presets),
                    serialNumber = 0L,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        StyledString(
                            "This is the room description for some room in Riverhaven. It didn't exist in our old preview, so we're putting arbitrary text here.",
                            style = WarlockStyle("roomdescription"),
                        ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    entireLineStyle = null,
                    serialNumber = 1L,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        (
                            StyledString("You also see a ") +
                                StyledString(
                                    "Sir Robyn",
                                    style = WarlockStyle.Bold,
                                ) + StyledString(".")
                        ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    serialNumber = 2L,
                    entireLineStyle = null,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        StyledString("say Hello", style = WarlockStyle.Command)
                            .toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    serialNumber = 3L,
                    entireLineStyle = null,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        (
                            StyledString(
                                "You say",
                                style = WarlockStyle.Speech,
                            ) + StyledString(", \"Hello.\"")
                        ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    serialNumber = 4L,
                    entireLineStyle = null,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        StyledString(
                            "Your mind hears Someone thinking, \"hello everyone\"",
                            style = WarlockStyle.Thought,
                        ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    serialNumber = 5L,
                    entireLineStyle = null,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        StyledString(
                            "Some text you are watching",
                            style = WarlockStyle.Watching,
                        ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    serialNumber = 6L,
                    entireLineStyle = null,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        (
                            StyledString(
                                "Someone whispers",
                                style = WarlockStyle.Whisper,
                            ) + StyledString(", \"Hi\"")
                        ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    serialNumber = 7L,
                    entireLineStyle = null,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
                StreamTextLine(
                    text =
                        StyledString(
                            " __      __              .__                 __    \n" +
                                "/  \\    /  \\_____ _______|  |   ____   ____ |  | __\n" +
                                "\\   \\/\\/   /\\__  \\\\_  __ \\  |  /  _ \\_/ ___\\|  |/ /\n" +
                                " \\        /  / __ \\|  | \\/  |_(  <_> )  \\___|    < \n" +
                                "  \\__/\\  /  (____  /__|  |____/\\____/ \\___  >__|_ \\\n" +
                                "       \\/        \\/                       \\/     \\/",
                            style = WarlockStyle.Mono,
                        ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
                    serialNumber = 8L,
                    entireLineStyle = null,
                    showWhenClosed = null,
                    isPrompt = false,
                ),
            )
        }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
        )
        Spacer(Modifier.height(16.dp))
        DefaultFontSettings(
            defaultFont = defaultFont,
            monoFont = monoFont,
            onSaveDefaultFont = { coroutineScope.launch { characterSettingsRepository.saveDefaultFont(currentCharacter.id, it) } },
            onSaveMonoFont = { coroutineScope.launch { characterSettingsRepository.saveMonoFont(currentCharacter.id, it) } },
        )
        Spacer(Modifier.height(16.dp))
        val barColor = presets["default"]?.textColor?.toColor() ?: MaterialTheme.colorScheme.onSurface
        CompositionLocalProvider(
            LocalContentColor provides (presets["default"]?.textColor?.toColor() ?: LocalContentColor.current),
        ) {
            ScrollableColumn(
                Modifier
                    .weight(1f)
                    .background(
                        presets["default"]?.backgroundColor?.toColor() ?: Color.Unspecified,
                    ).fillMaxWidth(),
                scrollbarStyle =
                    defaultScrollbarStyle(
                        thumbStyle =
                            ThumbStyle(
                                shape = RoundedCornerShape(4.dp),
                                unhoverColor = barColor.copy(alpha = 0.2f),
                                hoverColor = barColor,
                            ),
                    ),
            ) {
                previewLines.forEach { line ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    line.entireLineStyle?.backgroundColor?.toColor() ?: Color.Unspecified,
                                ).padding(horizontal = 4.dp),
                    ) {
                        line.text?.let {
                            Text(text = it)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PresetSettings(
            styleMap = presets,
            saveStyle = { name, styleDefinition ->
                coroutineScope.launch {
                    presetRepository.save(currentCharacter.id, name, styleDefinition)
                }
            },
        )
    }
}

/** The two character-wide font selectors: the normal font and the monospace font. */
@Composable
private fun DefaultFontSettings(
    defaultFont: FontConfig?,
    monoFont: FontConfig?,
    onSaveDefaultFont: (FontConfig?) -> Unit,
    onSaveMonoFont: (FontConfig?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // (current font, monospaceOnly).
    var editFont by remember { mutableStateOf<Pair<FontConfig?, Boolean>?>(null) }

    editFont?.let { (current, monospaceOnly) ->
        FontPickerDialog(
            current = current,
            monospaceOnly = monospaceOnly,
            onCloseRequest = { editFont = null },
            onSaveClick = { update ->
                if (monospaceOnly) onSaveMonoFont(update.toFontConfig()) else onSaveDefaultFont(update.toFontConfig())
                editFont = null
            },
        )
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                modifier = Modifier.width(120.dp).align(Alignment.CenterVertically),
                text = "Font",
            )
            OutlinedButton(onClick = { editFont = defaultFont to false }) {
                Text(defaultFont.fontLabel(), maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                modifier = Modifier.width(120.dp).align(Alignment.CenterVertically),
                text = "Monospace font",
            )
            OutlinedButton(onClick = { editFont = monoFont to true }) {
                Text(monoFont.fontLabel(), maxLines = 1)
            }
        }
    }
}

@Composable
fun ColumnScope.PresetSettings(
    styleMap: Map<String, StyleDefinition>,
    saveStyle: (name: String, StyleDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }

    if (editColor != null) {
        ColorPickerDialog(
            initialColor = editColor!!.first.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                editColor?.second?.invoke(color)
                editColor = null
            },
        )
    }
    ScrollableColumn(
        modifier = modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        styleMap.keys.forEach { preset ->
            // The monospace preset only flags text as monospace; its styling isn't user-editable.
            if (preset.equals("mono", ignoreCase = true)) return@forEach
            val style = styleMap[preset]
            if (style != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier.width(120.dp).align(Alignment.CenterVertically),
                        text = preset.replaceFirstChar { it.uppercase() },
                    )
                    ColorPickerButton(
                        text = "Content",
                        color = style.textColor.toColor(),
                        onClick = {
                            editColor =
                                Pair(style.textColor) { color ->
                                    saveStyle(
                                        preset,
                                        style.copy(textColor = color),
                                    )
                                }
                        },
                    )

                    ColorPickerButton(
                        text = "Background",
                        color = style.backgroundColor.toColor(),
                        onClick = {
                            editColor =
                                Pair(style.backgroundColor) { color ->
                                    saveStyle(
                                        preset,
                                        style.copy(backgroundColor = color),
                                    )
                                }
                        },
                    )

                    CheckboxRow(
                        checked = style.bold,
                        onCheckedChange = { saveStyle(preset, style.copy(bold = it)) },
                        text = "Bold",
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    CheckboxRow(
                        checked = style.italic,
                        onCheckedChange = { saveStyle(preset, style.copy(italic = it)) },
                        text = "Italic",
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    CheckboxRow(
                        checked = style.underline,
                        onCheckedChange = { saveStyle(preset, style.copy(underline = it)) },
                        text = "Underline",
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}
