package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class CharacterExport(
    val id: String,
    val gameCode: String,
    val name: String,
    val scriptDirectories: List<String>,
    val settings: Map<String, String>,
    val variables: Map<String, String>,
    val aliases: List<AliasExport>,
    val alterations: List<AlterationExport>,
    val highlights: List<HighlightExport>,
    val macros: List<MacroExport>,
    val names: List<NameExport>,
    val presets: List<PresetStyleExport>,
    val windows: List<WindowSettingsExport>,
)
