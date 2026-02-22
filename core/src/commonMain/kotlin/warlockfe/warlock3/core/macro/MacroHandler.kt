package warlockfe.warlock3.core.macro

interface MacroHandler {

    val entryText: CharSequence

    fun scroll(event: ScrollEvent)

    fun entryClearToEnd()

    fun entryClearToStart()

    fun entryDeleteLastWord()

    fun historyNext()

    fun historyPrev()

    fun entrySetCursorPosition(pos: Int)

    suspend fun pauseScripts()

    suspend fun repeatCommand(index: Int)

    fun submit()

    suspend fun stopScripts()
}