package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class AliasExport(
    val pattern: String, // pattern serves as unique id
    val replacement: String,
)
