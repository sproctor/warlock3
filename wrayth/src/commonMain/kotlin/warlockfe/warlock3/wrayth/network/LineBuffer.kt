package warlockfe.warlock3.wrayth.network

import warlockfe.warlock3.core.text.StyledString

/**
 * The text being assembled for the line currently being parsed, and the component being assembled
 * inside it, if any.
 *
 * A component's id and its content are one value here so they cannot get out of step. Two questions
 * are asked of this state by different callers - where appended text goes, and whether the line may
 * be flushed yet - and both answers turn on whether a component is open. Held apart, a caller could
 * answer them differently, and text would accumulate somewhere nothing ever flushed.
 */
internal class LineBuffer {
    private var line: StyledString? = null
    private var component: OpenComponent? = null

    private data class OpenComponent(
        val id: String,
        val text: StyledString,
    )

    /** Appends to the open component if there is one, and to the line otherwise. */
    fun append(text: StyledString) {
        val open = component
        if (open != null) {
            component = open.copy(text = open.text + text)
        } else {
            line = line?.plus(text) ?: text
        }
    }

    /** Starts a component, so that [append] gathers its content instead of the line's. */
    fun openComponent(id: String) {
        component = OpenComponent(id, StyledString())
    }

    /**
     * Ends the open component and hands back its id and content, or null when none was open - a
     * closing tag with nothing to close.
     */
    fun closeComponent(): Pair<String, StyledString>? =
        component?.let { open ->
            component = null
            open.id to open.text
        }

    /**
     * Abandons a half-gathered component, for the events that end a line's parsing while one is
     * still open - a prompt, or a switch to another stream. Its content is not wanted, but the
     * buffer has to go back to gathering the line's own text.
     */
    fun discardComponent() {
        component = null
    }

    /**
     * Takes the line's text and empties the buffer, or returns null while a component is still
     * open. An empty line comes back as an empty [StyledString] rather than null, because a blank
     * line is still a line.
     */
    fun takeLine(): StyledString? {
        if (component != null) return null
        val text = line ?: StyledString()
        line = null
        return text
    }
}
