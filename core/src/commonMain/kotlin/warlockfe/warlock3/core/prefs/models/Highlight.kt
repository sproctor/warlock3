package warlockfe.warlock3.core.prefs.models

import warlockfe.warlock3.core.text.StyleDefinition
import kotlin.uuid.Uuid

data class Highlight(
    val id: Uuid,
    val pattern: String,
    val styles: Map<Int, StyleDefinition>,
    val isRegex: Boolean,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
    val sound: String?,
)
