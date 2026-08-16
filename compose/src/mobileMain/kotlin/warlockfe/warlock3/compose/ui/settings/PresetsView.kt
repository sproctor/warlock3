package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import warlockfe.warlock3.compose.components.DEFAULT_PANEL_SCALE
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.GENERIC_SAMPLE
import warlockfe.warlock3.compose.components.MAX_PANEL_SCALE
import warlockfe.warlock3.compose.components.MIN_PANEL_SCALE
import warlockfe.warlock3.compose.components.PANEL_SCALE_HELP
import warlockfe.warlock3.compose.components.PANEL_SCALE_STEP
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.StyleChip
import warlockfe.warlock3.compose.components.StyleSample
import warlockfe.warlock3.compose.components.TextStyleEditor
import warlockfe.warlock3.compose.components.backgroundLabel
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.formatPanelScale
import warlockfe.warlock3.compose.components.sampleFor
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.toColor
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
import kotlin.math.roundToInt

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

/** The editor preview's sample: the in-game line this preset styles, so the preview reads like real output. */
private fun PresetItem.sample(): StyleSample =
    when (this) {
        PresetItem.Base -> GENERIC_SAMPLE
        is PresetItem.Named -> sampleFor(name)
    }

/**
 * The Appearance -> Presets page: a Global/character scope selector over a master list of the base
 * "default text" plus the named presets (each drawn in its own resolved style), drilling in to the
 * shared [TextStyleEditor] for the selected scope. Also hosts the character's monospace font, which is
 * not part of any style layer.
 */
@Composable
fun PresetsView(
    presetRepository: PresetRepository,
    characterSettingsRepository: CharacterSettingsRepository,
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
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
    val panelFont by remember(scopeId) { characterSettingsRepository.observePanelFont(scopeId) }.collectAsState(null)
    val panelScale by remember(scopeId) { characterSettingsRepository.observePanelScale(scopeId) }.collectAsState(null)

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
    val windowBackground =
        when (val bg = resolve(baseLayers).background) {
            is Background.Fill -> bg.color.toColor(default = SAFE_DEFAULT_STYLE.backgroundColor.toColor())
            else -> SAFE_DEFAULT_STYLE.backgroundColor.toColor()
        }

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
            FontRow(
                label = "Monospace font",
                font = monoFont,
                monospaceOnly = true,
                onSave = { scope.launch { characterSettingsRepository.saveMonoFont(scopeId, it) } },
            )
            // Panel windows are widget chrome rather than prose, so they get their own font and their
            // own geometry scale instead of following the base text style above.
            FontRow(
                label = "Panel font",
                font = panelFont,
                monospaceOnly = false,
                onSave = { scope.launch { characterSettingsRepository.savePanelFont(scopeId, it) } },
            )
            PanelScaleRow(
                scale = panelScale,
                onSave = { scope.launch { characterSettingsRepository.savePanelScale(scopeId, it) } },
            )
            Spacer(Modifier.height(16.dp))
            Text("Presets", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                WarlockStyle.presets.forEach { style ->
                    val item = PresetItem.Named(style.name)
                    PresetListRow(item, chipStyle(item), windowBackground) { selectedItem = item }
                }
            }
        } else {
            val model = modelFor(current)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectedItem = null }) { Text("< Back") }
                Text(current.label())
            }
            Spacer(Modifier.height(12.dp))
            ScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                TextStyleEditor(
                    sourced = model.sourced,
                    sample = model.sample,
                    editScope = model.editScope,
                    editLayer = model.editLayer,
                    onSave = { save(current, it) },
                    windowBackground = windowBackground,
                    inheritedBackground = inheritedBackground(current),
                    palette = palette,
                    sampleLine = current.sample(),
                    baseStyle = resolve(baseLayers),
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
        Text(backgroundLabel(resolved.background), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One of the character's standalone fonts (monospace, panel), which stand apart from the style layers. */
@Composable
private fun FontRow(
    label: String,
    font: FontConfig?,
    monospaceOnly: Boolean,
    onSave: (FontConfig?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        FontPickerDialog(
            current = font,
            monospaceOnly = monospaceOnly,
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
        Text(label, modifier = Modifier.width(120.dp))
        OutlinedButton(onClick = { editing = true }) { Text(font.fontLabel()) }
    }
}

/**
 * The character's panel scale. Panels are the only windows the game lays out in pixels, and it sizes
 * those for Wrayth's grid, so this is the knob for buying room without changing the text size.
 */
@Composable
private fun PanelScaleRow(
    scale: Float?,
    onSave: (Float?) -> Unit,
) {
    val current = scale ?: DEFAULT_PANEL_SCALE

    fun step(delta: Float) {
        val next = ((current + delta) * 10f).roundToInt() / 10f
        onSave(next.coerceIn(MIN_PANEL_SCALE, MAX_PANEL_SCALE))
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Panel scale", modifier = Modifier.width(120.dp))
            OutlinedButton(onClick = { step(-PANEL_SCALE_STEP) }) { Text("-") }
            Text(formatPanelScale(current), modifier = Modifier.width(48.dp))
            OutlinedButton(onClick = { step(PANEL_SCALE_STEP) }) { Text("+") }
            OutlinedButton(onClick = { onSave(null) }) { Text("Reset") }
        }
        Text(
            PANEL_SCALE_HELP,
            modifier = Modifier.padding(start = 128.dp, top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
