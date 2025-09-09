package warlockfe.warlock3.core.window

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.toPersistentSet
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.StyledStringLeaf
import warlockfe.warlock3.core.text.StyledStringSubstring
import warlockfe.warlock3.core.text.StyledStringVariable

interface TextStream {

    val id: String

    suspend fun appendPartial(text: StyledString)

    suspend fun appendPartialAndEol(text: StyledString)

    suspend fun clear()

    suspend fun appendLine(text: StyledString, ignoreWhenBlank: Boolean = false)

    suspend fun updateComponent(name: String, value: StyledString)

    suspend fun appendResource(url: String)
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
