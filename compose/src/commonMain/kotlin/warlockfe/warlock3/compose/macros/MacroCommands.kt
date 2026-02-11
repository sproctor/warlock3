package warlockfe.warlock3.compose.macros

import androidx.compose.ui.text.TextRange
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.ScrollEvent

object MacroCommands {
    private val commandMap = mapOf<String, suspend (GameViewModel) -> Unit>(
        "bufferend" to { viewModel ->
            viewModel.scroll(ScrollEvent.BUFFER_END)
        },
        "bufferstart" to { viewModel ->
            viewModel.scroll(ScrollEvent.BUFFER_START)
        },
        "cleartoend" to { viewModel ->
            val text = viewModel.entryText
            viewModel.entryDelete(TextRange(text.selection.end, text.text.length))
        },
        "cleartostart" to { viewModel ->
            val text = viewModel.entryText
            viewModel.entryDelete(TextRange(0, text.selection.start))
        },
        "deletelastword" to { viewModel ->
            val text = viewModel.entryText
            val index = text.text.substring(0, text.selection.start).trim().lastIndexOfAny(listOf(" ", "\t")) + 1
            if (index < text.text.length) {
                viewModel.entryDelete(TextRange(index, text.selection.start))
            }
        },
        "linedown" to { viewModel ->
            viewModel.scroll(ScrollEvent.LINE_DOWN)
        },
        "lineup" to { viewModel ->
            viewModel.scroll(ScrollEvent.LINE_UP)
        },
        "movecursortoend" to { viewModel ->
            viewModel.entrySetSelection(TextRange(viewModel.entryText.text.length))
        },
        "movecursortostart" to { viewModel ->
            viewModel.entrySetSelection(TextRange(0))
        },
        "nexthistory" to { viewModel ->
            viewModel.historyNext()
        },
        "pagedown" to { viewModel ->
            viewModel.scroll(ScrollEvent.PAGE_DOWN)
        },
        "pageup" to { viewModel ->
            viewModel.scroll(ScrollEvent.PAGE_UP)
        },
        "pausescript" to { viewModel ->
            viewModel.pauseScripts()
        },
        "prevhistory" to { viewModel ->
            viewModel.historyPrev()
        },
        "repeatlast" to { viewModel ->
            viewModel.repeatCommand(1)
        },
        "returnorrepeatlast" to { viewModel ->
            if (viewModel.entryText.text.isBlank()) {
                viewModel.repeatCommand(1)
            } else {
                viewModel.submit()
            }
        },
        "repeatsecondtolast" to { viewModel ->
            viewModel.repeatCommand(2)
        },
        "stopscript" to { viewModel ->
            viewModel.stopScripts()
        },
    )

    suspend fun execute(command: String, viewModel: GameViewModel): Boolean {
        val commandFunction = commandMap[command.lowercase()] ?: return false
        commandFunction(viewModel)
        return true
    }
}
