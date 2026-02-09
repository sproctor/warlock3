package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class NameExport(
    val text: String,
    val sound: String?,
    val style: StyleExport,
)
