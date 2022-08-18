package cc.warlock.warlock3.core.window

import cc.warlock.warlock3.core.text.StyledString
import cc.warlock.warlock3.core.text.StyledStringLeaf
import cc.warlock.warlock3.core.text.StyledStringSubstring
import cc.warlock.warlock3.core.text.StyledStringVariable
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentSet

interface TextStream {

    val name: String

    suspend fun appendPartial(text: StyledString)

    suspend fun appendEol()

    suspend fun clear()

    suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean = false)
}

fun StyledString.getComponents(): PersistentSet<String> {
    return substrings.mapNotNull { it.getComponent() }.toPersistentSet()
}

fun StyledStringLeaf.getComponent(): String? {
    return when (this) {
        is StyledStringVariable -> name
        is StyledStringSubstring -> null
    }
}

data class StreamLine(
    val ignoreWhenBlank: Boolean,
    val text: StyledString,
    val serialNumber: Long,
)
