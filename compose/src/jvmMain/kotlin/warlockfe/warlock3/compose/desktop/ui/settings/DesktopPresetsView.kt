package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.StyleChip
import warlockfe.warlock3.compose.components.backgroundLabel
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.desktop.components.DesktopFontPickerDialog
import warlockfe.warlock3.compose.desktop.components.DesktopTextStyleEditor
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.ui.settings.resolvedWindowBackground
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColorPalette
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.ResolvedStyle
import warlockfe.warlock3.core.text.StyleEditorModel
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.resolveRefs
import warlockfe.warlock3.core.text.styleEditorModel
import warlockfe.warlock3.core.text.toLayer

/** An entry in the presets master list: the base "default text", or one of the named style presets. */
private sealed interface PresetItem {
    data object Base : PresetItem

    data class Named(
        val name: String,
    ) : PresetItem
}

private fun PresetItem.label(): String =
    when (this) {
        PresetItem.Base -> "Default text"
        is PresetItem.Named -> name.replaceFirstChar { it.uppercase() }
    }

/**
 * The Appearance -> Presets page: a Global/character scope selector over a master list of the base
 * "default text" plus the named presets (each drawn in its own resolved style), drilling in to the
 * shared [DesktopTextStyleEditor] for the selected scope. Also hosts the character's monospace font,
 * which is not part of any style layer.
 */
@Composable
fun DesktopPresetsView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    presetRepository: PresetRepository,
    characterSettingsRepository: CharacterSettingsRepository,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(initialCharacter) { mutableStateOf(initialCharacter) }
    var selectedItem by remember { mutableStateOf<PresetItem?>(null) }
    val scope = rememberCoroutineScope()

    // null character = the Global (all-characters) scope; edits then target the global layer.
    val editingCharacterId = selectedCharacter?.id
    val scopeId = editingCharacterId ?: GLOBAL_CHARACTER_ID

    val skin = LocalSkin.current
    val isDark = LocalDarkTheme.current
    val skinLayers = remember(skin, isDark) { skin.toPresets(isDark).mapValues { it.value.toLayer() } }
    val skinBase = remember(skin, isDark) { skin.toPresets(isDark)["default"]?.toLayer() ?: StyleLayer() }

    val charPresets by remember(scopeId) { presetRepository.observeScopeLayers(scopeId) }.collectAsState(emptyMap())
    val globalPresets by remember { presetRepository.observeScopeLayers(GLOBAL_CHARACTER_ID) }.collectAsState(emptyMap())
    val charBase by remember(scopeId) { characterSettingsRepository.observeBaseStyle(scopeId) }.collectAsState(StyleLayer())
    val globalBase by remember { characterSettingsRepository.observeBaseStyle(GLOBAL_CHARACTER_ID) }.collectAsState(StyleLayer())
    val monoFont by remember(scopeId) { characterSettingsRepository.observeMonoFont(scopeId) }.collectAsState(null)

    // The skin's named-color palette, and the user layers with their skin-referenced colors resolved
    // against it (the ref is kept, so the editor can still show a color as skin-tracked).
    val palette = remember(skin, isDark) { skin.toColorPalette(isDark) }
    val charBaseR = charBase.resolveRefs(palette)
    val globalBaseR = globalBase.resolveRefs(palette)
    val charPresetsR = charPresets.mapValues { it.value.resolveRefs(palette) }
    val globalPresetsR = globalPresets.mapValues { it.value.resolveRefs(palette) }

    fun modelFor(item: PresetItem): StyleEditorModel =
        when (item) {
            PresetItem.Base -> {
                styleEditorModel(
                    characterLayer = if (editingCharacterId != null) charBaseR else null,
                    globalLayer = globalBaseR,
                    skinLayer = skinBase,
                )
            }

            is PresetItem.Named -> {
                styleEditorModel(
                    characterLayer = if (editingCharacterId != null) (charPresetsR[item.name] ?: StyleLayer()) else null,
                    globalLayer = globalPresetsR[item.name] ?: StyleLayer(),
                    skinLayer = skinLayers[item.name] ?: StyleLayer(),
                )
            }
        }

    fun save(
        item: PresetItem,
        layer: StyleLayer,
    ) {
        scope.launch {
            when (item) {
                PresetItem.Base -> characterSettingsRepository.saveBaseStyle(scopeId, layer)
                is PresetItem.Named -> presetRepository.saveLayer(scopeId, item.name, layer)
            }
        }
    }

    // The effective game window background (the resolved base background) that the list chips and the
    // editor preview composite against - never the settings panel surface.
    val baseLayers =
        listOfNotNull(
            charBaseR.takeIf { editingCharacterId != null },
            globalBaseR,
            skinBase,
        )
    val windowBackground = resolvedWindowBackground(baseLayers)

    // The full cascade for an item, edit scope first: how it renders over the base, so unset attributes
    // inherit the base as in game.
    fun chipStack(item: PresetItem): List<StyleLayer> =
        when (item) {
            PresetItem.Base -> {
                baseLayers
            }

            is PresetItem.Named -> {
                listOfNotNull(
                    (charPresetsR[item.name] ?: StyleLayer()).takeIf { editingCharacterId != null },
                    globalPresetsR[item.name] ?: StyleLayer(),
                    skinLayers[item.name] ?: StyleLayer(),
                ) + baseLayers
            }
        }

    fun chipStyle(item: PresetItem): ResolvedStyle = resolve(chipStack(item))

    // What the background would fall back to if the edited scope unset it (the layers below it).
    fun inheritedBackground(item: PresetItem): Background = resolve(chipStack(item).drop(1)).background

    SettingsListScaffold(
        title = "Presets",
        selectedCharacter = selectedCharacter,
        characters = characters,
        onSelectCharacter = { selectedCharacter = it },
        modifier = modifier.fillMaxSize(),
    ) {
        val current = selectedItem
        if (current == null) {
            // Base text and the monospace font stand apart from the named presets, so they sit above the
            // "Presets" heading rather than in the preset list.
            PresetListRow(PresetItem.Base, chipStyle(PresetItem.Base), windowBackground) { selectedItem = PresetItem.Base }
            MonoFontRow(
                monoFont = monoFont,
                onSave = { scope.launch { characterSettingsRepository.saveMonoFont(scopeId, it) } },
            )
            Spacer(Modifier.height(16.dp))
            Text("Presets")
            Spacer(Modifier.height(8.dp))
            WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                WarlockStyle.presets.forEach { style ->
                    val item = PresetItem.Named(style.name)
                    PresetListRow(item, chipStyle(item), windowBackground) { selectedItem = item }
                }
            }
        } else {
            val model = modelFor(current)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarlockOutlinedButton(onClick = { selectedItem = null }, text = "< Back")
                Text(current.label())
            }
            Spacer(Modifier.height(12.dp))
            WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                DesktopTextStyleEditor(
                    sourced = model.sourced,
                    sample = model.sample,
                    editScope = model.editScope,
                    editLayer = model.editLayer,
                    onSave = { save(current, it) },
                    windowBackground = windowBackground,
                    inheritedBackground = inheritedBackground(current),
                    palette = palette,
                )
            }
        }
    }
}

/**
 * A master-list row: the honest style chip, the item's label in normal (always-legible) UI color - never
 * its own style - and a muted trailing background label.
 */
@Composable
private fun PresetListRow(
    item: PresetItem,
    resolved: ResolvedStyle,
    windowBackground: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StyleChip(resolved = resolved, windowBackground = windowBackground)
        Text(item.label(), modifier = Modifier.weight(1f))
        Text(backgroundLabel(resolved.background), color = LocalContentColor.current.copy(alpha = 0.6f))
    }
}

/** The character's monospace font, which stands apart from the style layers. */
@Composable
private fun MonoFontRow(
    monoFont: FontConfig?,
    onSave: (FontConfig?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        DesktopFontPickerDialog(
            current = monoFont,
            monospaceOnly = true,
            onCloseRequest = { editing = false },
            onSaveClick = {
                onSave(it.toFontConfig())
                editing = false
            },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Monospace font", modifier = Modifier.width(120.dp))
        WarlockOutlinedButton(onClick = { editing = true }, text = monoFont.fontLabel())
    }
}
