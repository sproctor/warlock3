package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class PresetStyleExport(
    val id: String,
    val style: StyleExport,
)
