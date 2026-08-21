package warlockfe.warlock3.core.macro

interface MacroHandler {
    val entryText: CharSequence

    fun scroll(event: ScrollEvent)

    fun entryClearToEnd()

    fun entryClearToStart()

    fun entryDeleteLastWord()

    fun historyNext()

    fun historyPrev()

    fun historySearch()

    fun historySearchExit()

    fun findNext()

    fun findPrev()

    /** Copies the focused window's selected text to the clipboard. */
    suspend fun copySelection()

    /** Selects all of the focused window's composed text. */
    fun selectAll()

    /** Inserts the clipboard's text into the command entry at the cursor. */
    suspend fun pasteIntoEntry()

    /**
     * Moves the command entry's selected text to the clipboard. Does nothing unless the entry has
     * focus: a window's text is the game's, not ours to remove.
     */
    suspend fun cutEntrySelection()

    fun entrySetCursorPosition(pos: Int)

    suspend fun pauseScripts()

    suspend fun repeatCommand(index: Int)

    fun submit()

    suspend fun stopScripts()
}
