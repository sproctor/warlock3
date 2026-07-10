package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.components.TextStyleEditor
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColor
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

    fun modelFor(item: PresetItem): StyleEditorModel =
        when (item) {
            PresetItem.Base -> {
                styleEditorModel(
                    characterLayer = if (editingCharacterId != null) charBase else null,
                    globalLayer = globalBase,
                    skinLayer = skinBase,
                )
            }

            is PresetItem.Named -> {
                styleEditorModel(
                    characterLayer = if (editingCharacterId != null) (charPresets[item.name] ?: StyleLayer()) else null,
                    globalLayer = globalPresets[item.name] ?: StyleLayer(),
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

    SettingsListScaffold(
        title = "Presets",
        selectedCharacter = selectedCharacter,
        characters = characters,
        onSelectCharacter = { selectedCharacter = it },
        modifier = modifier.fillMaxSize(),
    ) {
        val current = selectedItem
        if (current == null) {
            ScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                PresetListRow(PresetItem.Base, modelFor(PresetItem.Base).sample) { selectedItem = PresetItem.Base }
                WarlockStyle.presets.forEach { style ->
                    val item = PresetItem.Named(style.name)
                    PresetListRow(item, modelFor(item).sample) { selectedItem = item }
                }
            }
            MonoFontRow(
                monoFont = monoFont,
                onSave = { scope.launch { characterSettingsRepository.saveMonoFont(scopeId, it) } },
            )
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
                )
            }
        }
    }
}

/** A master-list row: a swatch plus the item's label drawn in that item's own resolved style. */
@Composable
private fun PresetListRow(
    item: PresetItem,
    sample: ResolvedStyle,
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
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background((sample.background as? Background.Fill)?.color.toColor(default = Color(0xFF1E1F22))),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "Aa",
                style = TextStyle(color = sample.textColor.toColor(default = Color(0xFFF0F0FF)), fontSize = 12.sp),
            )
        }
        BasicText(
            text = item.label(),
            style =
                TextStyle(
                    color = sample.textColor.toColor(default = Color(0xFFF0F0FF)),
                    fontWeight = sample.weight?.let { FontWeight(it) },
                    fontStyle = if (sample.italic) FontStyle.Italic else null,
                    textDecoration = if (sample.underline) TextDecoration.Underline else null,
                ),
        )
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
        FontPickerDialog(
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
        OutlinedButton(onClick = { editing = true }) { Text(monoFont.fontLabel()) }
    }
}
