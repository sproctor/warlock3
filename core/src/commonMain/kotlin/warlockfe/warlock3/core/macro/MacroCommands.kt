package warlockfe.warlock3.core.macro

object MacroCommands {
    val commands = listOf(
        MacroCommand("bufferend") {
            it.scroll(ScrollEvent.BUFFER_END)
        },
        MacroCommand("bufferstart") {
            it.scroll(ScrollEvent.BUFFER_START)
        },
        MacroCommand("cleartoend") {
            it.entryClearToEnd()
        },
        MacroCommand("cleartostart") {
            it.entryClearToStart()
        },
        MacroCommand("deletelastword") {
            it.entryDeleteLastWord()
        },
        MacroCommand("historynext", listOf("nexthistory")) {
            it.historyNext()
        },
        MacroCommand("historyprev", listOf("prevhistory")) {
            it.historyPrev()
        },
        MacroCommand("linedown") {
            it.scroll(ScrollEvent.LINE_DOWN)
        },
        MacroCommand("lineup") {
            it.scroll(ScrollEvent.LINE_UP)
        },
        MacroCommand("movecursortoend") {
            it.entrySetCursorPosition(it.entryText.length)
        },
        MacroCommand("movecursortostart") {
            it.entrySetCursorPosition(0)
        },
        MacroCommand("pagedown") {
            it.scroll(ScrollEvent.PAGE_DOWN)
        },
        MacroCommand("pageup") {
            it.scroll(ScrollEvent.PAGE_UP)
        },
        MacroCommand("pausescript", listOf("pausescripts")) {
            it.pauseScripts()
        },
        MacroCommand("repeatlast") {
            it.repeatCommand(1)
        },
        MacroCommand("returnorrepeatlast") {
            if (it.entryText.isBlank()) {
                it.repeatCommand(1)
            } else {
                it.submit()
            }
        },
        MacroCommand("repeatsecondtolast") {
            it.repeatCommand(2)
        },
        MacroCommand("stopscript", listOf("stopscripts")) {
            it.stopScripts()
        },
    )

    private val commandMap = mutableMapOf<String, MacroCommand>()

    init {
        commands.forEach { command ->
            commandMap[command.name] = command
            command.aliases.forEach { alias ->
                commandMap[alias] = command
            }
        }
    }

    suspend fun execute(command: String, macroHandler: MacroHandler): Boolean {
        val macroCommand = commandMap[command.lowercase()] ?: return false
        macroCommand.execute(macroHandler)
        return true
    }
}
