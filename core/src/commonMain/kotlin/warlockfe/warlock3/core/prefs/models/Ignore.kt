package warlockfe.warlock3.core.prefs.models

import kotlin.uuid.Uuid

enum class IgnoreMatchMode {
    /** The text may appear anywhere in the line. */
    CONTAINS,

    /** The text must appear as a whole word (same word-boundary rules as highlights). */
    WORD,

    /** The text must match the entire line exactly. */
    LINE,

    ;

    companion object {
        // Lenient on purpose: the mode is stored as a hand-editable string in ignores.toml, and a bad
        // value must not fail the whole file decode.
        fun fromString(value: String?): IgnoreMatchMode = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CONTAINS
    }
}

data class Ignore(
    val id: Uuid,
    val pattern: String,
    val isRegex: Boolean,
    // Only meaningful for text ignores; a regex expresses word/line anchoring itself.
    val matchMode: IgnoreMatchMode,
    val ignoreCase: Boolean,
)
