package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerButton
import warlockfe.warlock3.compose.desktop.components.DesktopColorPickerDialog
import warlockfe.warlock3.compose.desktop.components.DesktopFontPickerDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.WarlockStyle

/** The character's default text fonts plus the 10 skin/style presets (text/background/bold/italic/underline). */
@Composable
fun DesktopPresetsView(
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
    val coroutineScope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
        )
        Spacer(Modifier.height(16.dp))

        WarlockScrollableColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            DefaultFontSettings(
                defaultFont = defaultFont,
                monoFont = monoFont,
                onSaveDefaultFont = { coroutineScope.launch { characterSettingsRepository.saveDefaultFont(currentCharacter.id, it) } },
                onSaveMonoFont = { coroutineScope.launch { characterSettingsRepository.saveMonoFont(currentCharacter.id, it) } },
            )
            Spacer(Modifier.height(16.dp))

            PresetSettings(
                styleMap = presets,
                saveStyle = { name, styleDefinition ->
                    coroutineScope.launch {
                        presetRepository.save(currentCharacter.id, name, styleDefinition)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
        }
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

    editFont?.let { (current, monospaceOnly) ->
        DesktopFontPickerDialog(
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
            WarlockOutlinedButton(
                onClick = { editFont = defaultFont to false },
                text = defaultFont.fontLabel(),
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
        }
    }
}

@Composable
private fun PresetSettings(
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

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WarlockStyle.presets.forEach { warlockStyle ->
            val preset = warlockStyle.name.ifBlank { "default" }
            val style = styleMap[preset] ?: StyleDefinition()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    modifier =
                        Modifier
                            .width(120.dp)
                            .align(Alignment.CenterVertically),
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
                WarlockCheckboxRow(
                    checked = style.bold,
                    onCheckedChange = { saveStyle(preset, style.copy(bold = it)) },
                    text = "Bold",
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                WarlockCheckboxRow(
                    checked = style.italic,
                    onCheckedChange = { saveStyle(preset, style.copy(italic = it)) },
                    text = "Italic",
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                WarlockCheckboxRow(
                    checked = style.underline,
                    onCheckedChange = { saveStyle(preset, style.copy(underline = it)) },
                    text = "Underline",
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}
