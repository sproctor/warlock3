package warlockfe.warlock3.core.prefs.models

import java.util.UUID

data class Alteration(
    val id: UUID,
    val pattern: String,
    val sourceStream: String?,
    val destinationStream: String?,
    val result: String?,
    val ignoreCase: Boolean,
    val keepOriginal: Boolean,
)