package warlockfe.warlock3.compose.desktop.ui.settings

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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerButton
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerDialog
import warlockfe.warlock3.compose.desktop.components.DesktopFontPickerDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
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
import warlockfe.warlock3.core.text.isSpecified

@Composable
fun DesktopAppearanceView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    presetRepository: PresetRepository,
    characterSettingsRepository: CharacterSettingsRepository,
    modifier: Modifier = Modifier,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) {
            mutableStateOf(initialCharacter ?: characters.firstOrNull())
        }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created", modifier = modifier.padding(16.dp))
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
    val previewLines = remember(presets, monoFont) { buildPreviewLines(presets, monoFont) }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
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

        val defaultStyle = presets["default"]
        val previewBackground = defaultStyle?.backgroundColor?.toColor() ?: Color.Unspecified
        val previewContentColor =
            if (defaultFont?.textColor?.isSpecified() == true) {
                defaultFont?.textColor.toColor()
            } else {
                defaultStyle?.textColor?.toColor() ?: LocalContentColor.current
            }
        CompositionLocalProvider(LocalContentColor provides previewContentColor) {
            WarlockScrollableColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(previewBackground),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                previewLines.forEach { line ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(line.entireLineStyle?.backgroundColor?.toColor() ?: Color.Unspecified)
                                .padding(horizontal = 4.dp),
                    ) {
                        line.text?.let { Text(text = it) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        PresetSettings(
            styleMap = presets,
            modifier = Modifier.weight(1f),
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
    // Which selector's picker is open, plus whether to restrict it to monospace families.
    var editFont by remember { mutableStateOf<Pair<FontConfig?, Boolean>?>(null) }
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }

    editFont?.let { (current, monospaceOnly) ->
        DesktopFontPickerDialog(
            current = current,
            monospaceOnly = monospaceOnly,
            onCloseRequest = { editFont = null },
            onSaveClick = { update ->
                val fontUpdate = update.toFontConfig().withTextColorFrom(current)
                if (monospaceOnly) onSaveMonoFont(fontUpdate) else onSaveDefaultFont(fontUpdate)
                editFont = null
            },
        )
    }
    editColor?.let { (initialColor, onPick) ->
        DesktopColorPickerDialog(
            initialColor = initialColor.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                onPick(color)
                editColor = null
            },
        )
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                modifier = Modifier.width(120.dp).align(Alignment.CenterVertically),
                text = "Font",
            )
            WarlockOutlinedButton(
                onClick = { editFont = defaultFont to false },
                text = defaultFont.fontLabel(),
            )
            DesktopColorPickerButton(
                text = "Color",
                color = defaultFont?.textColor.toColor(),
                onClick = {
                    editColor =
                        Pair(defaultFont?.textColor ?: WarlockColor.Unspecified) { color ->
                            onSaveDefaultFont(defaultFont.withTextColor(color))
                        }
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                modifier = Modifier.width(120.dp).align(Alignment.CenterVertically),
                text = "Monospace font",
            )
            WarlockOutlinedButton(
                onClick = { editFont = monoFont to true },
                text = monoFont.fontLabel(),
            )
            DesktopColorPickerButton(
                text = "Color",
                color = monoFont?.textColor.toColor(),
                onClick = {
                    editColor =
                        Pair(monoFont?.textColor ?: WarlockColor.Unspecified) { color ->
                            onSaveMonoFont(monoFont.withTextColor(color))
                        }
                },
            )
        }
    }
}

private fun FontConfig?.withTextColorFrom(current: FontConfig?): FontConfig? {
    val textColor = current?.textColor ?: WarlockColor.Unspecified
    return (this ?: FontConfig()).copy(textColor = textColor).takeUnless { it.isEmpty() }
}

private fun FontConfig?.withTextColor(color: WarlockColor): FontConfig? =
    (this ?: FontConfig()).copy(textColor = color).takeUnless { it.isEmpty() }

private const val MIN_PRESET_FONT_SIZE = 6f
private const val MAX_PRESET_FONT_SIZE = 72f

@Composable
private fun ColumnScope.PresetSettings(
    styleMap: Map<String, StyleDefinition>,
    saveStyle: (name: String, style: StyleDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editColor by remember { mutableStateOf<Pair<WarlockColor, (WarlockColor) -> Unit>?>(null) }

    editColor?.let { (initial, onPick) ->
        DesktopColorPickerDialog(
            initialColor = initial.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                onPick(color)
                editColor = null
            },
        )
    }

    WarlockScrollableColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WarlockStyle.presets.forEach { warlockStyle ->
            val preset = warlockStyle.name.ifBlank { "default" }
            val style = styleMap[preset] ?: StyleDefinition()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.width(120.dp),
                    text = preset.replaceFirstChar { it.uppercase() },
                )
                DesktopColorPickerButton(
                    text = "Content",
                    color = style.textColor.toColor(),
                    onClick = {
                        editColor =
                            Pair(style.textColor) { color ->
                                saveStyle(preset, style.copy(textColor = color))
                            }
                    },
                )
                DesktopColorPickerButton(
                    text = "Background",
                    color = style.backgroundColor.toColor(),
                    onClick = {
                        editColor =
                            Pair(style.backgroundColor) { color ->
                                saveStyle(preset, style.copy(backgroundColor = color))
                            }
                    },
                )
                FontSizeSetting(
                    fontSize = style.fontSize,
                    onFontSizeChange = { saveStyle(preset, style.copy(fontSize = it)) },
                )
                StyleToggleButton(
                    selected = style.bold,
                    onSelectedChange = { saveStyle(preset, style.copy(bold = it)) },
                    label = "B",
                )
                StyleToggleButton(
                    selected = style.italic,
                    onSelectedChange = { saveStyle(preset, style.copy(italic = it)) },
                    label = "I",
                )
                StyleToggleButton(
                    selected = style.underline,
                    onSelectedChange = { saveStyle(preset, style.copy(underline = it)) },
                    label = "U",
                )
            }
        }
    }
}

@Composable
private fun StyleToggleButton(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = modifier.width(40.dp)
    if (selected) {
        WarlockButton(
            onClick = { onSelectedChange(false) },
            modifier = buttonModifier,
            text = label,
        )
    } else {
        WarlockOutlinedButton(
            onClick = { onSelectedChange(true) },
            modifier = buttonModifier,
            text = label,
        )
    }
}

@Composable
private fun FontSizeSetting(
    fontSize: Float?,
    onFontSizeChange: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeState = rememberTextFieldState(fontSize?.toFontSizeText().orEmpty())

    LaunchedEffect(fontSize) {
        val text = fontSize?.toFontSizeText().orEmpty()
        if (sizeState.text.toString() != text) {
            sizeState.setTextAndPlaceCursorAtEnd(text)
        }
    }
    LaunchedEffect(sizeState, fontSize) {
        snapshotFlow { sizeState.text.toString() }
            .collectLatest { text ->
                val next =
                    when {
                        text.isBlank() -> null
                        else -> text.toFloatOrNull()?.coerceIn(MIN_PRESET_FONT_SIZE, MAX_PRESET_FONT_SIZE)
                    }
                if (next != null || text.isBlank()) {
                    if (next != fontSize) onFontSizeChange(next)
                }
            }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Size")
        WarlockTextField(
            state = sizeState,
            modifier = Modifier.width(72.dp),
            placeholder = "Default",
        )
    }
}

private fun Float.toFontSizeText(): String = if (this == toInt().toFloat()) toInt().toString() else toString().trimEnd('0').trimEnd('.')

private fun buildPreviewLines(
    presets: Map<String, StyleDefinition>,
    monoFont: FontConfig?,
): List<StreamTextLine> =
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
                        StyledString("Sir Robyn", style = WarlockStyle.Bold) +
                        StyledString(".")
                ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
            entireLineStyle = null,
            serialNumber = 2L,
            showWhenClosed = null,
            isPrompt = false,
        ),
        StreamTextLine(
            text =
                StyledString("say Hello", style = WarlockStyle.Command)
                    .toAnnotatedString(emptyMap(), presets, {}, monoFont),
            entireLineStyle = null,
            serialNumber = 3L,
            showWhenClosed = null,
            isPrompt = false,
        ),
        StreamTextLine(
            text =
                (
                    StyledString("You say", style = WarlockStyle.Speech) +
                        StyledString(", \"Hello.\"")
                ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
            entireLineStyle = null,
            serialNumber = 4L,
            showWhenClosed = null,
            isPrompt = false,
        ),
        StreamTextLine(
            text =
                StyledString(
                    "Your mind hears Someone thinking, \"hello everyone\"",
                    style = WarlockStyle.Thought,
                ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
            entireLineStyle = null,
            serialNumber = 5L,
            showWhenClosed = null,
            isPrompt = false,
        ),
        StreamTextLine(
            text =
                StyledString(
                    "Some text you are watching",
                    style = WarlockStyle.Watching,
                ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
            entireLineStyle = null,
            serialNumber = 6L,
            showWhenClosed = null,
            isPrompt = false,
        ),
        StreamTextLine(
            text =
                (
                    StyledString("Someone whispers", style = WarlockStyle.Whisper) +
                        StyledString(", \"Hi\"")
                ).toAnnotatedString(emptyMap(), presets, {}, monoFont),
            entireLineStyle = null,
            serialNumber = 7L,
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
                ).applyMonospace().toAnnotatedString(emptyMap(), presets, {}, monoFont),
            entireLineStyle = null,
            serialNumber = 8L,
            showWhenClosed = null,
            isPrompt = false,
        ),
    )
