package warlockfe.warlock3.core.prefs.models

import warlockfe.warlock3.core.text.StyleDefinition
import java.util.UUID

data class Highlight(
    val id: UUID,
    val pattern: String,
    val styles: Map<Int, StyleDefinition>,
    val isRegex: Boolean,
    val matchPartialWord: Boolean,
    val ignoreCase: Boolean,
)
