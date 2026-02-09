package warlockfe.warlock3.core.prefs.export

import kotlinx.serialization.Serializable

@Serializable
data class AlterationExport(
    val pattern: String, // pattern serves as unique id
    val sourceStream: String?,
    val destinationStream: String?,
    val result: String?,
    val ignoreCase: Boolean,
    val keepOriginal: Boolean,
)
