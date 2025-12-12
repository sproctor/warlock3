package warlockfe.warlock3.compose.macros

import androidx.compose.ui.text.TextRange
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.ScrollEvent

val macroCommands = mapOf<String, suspend (GameViewModel) -> Unit>(
    "bufferend" to { viewModel ->
        viewModel.scroll(ScrollEvent.BUFFER_END)
    },
    "bufferstart" to { viewModel ->
        viewModel.scroll(ScrollEvent.BUFFER_START)
    },
    "cleartostart" to { viewModel ->
        val text = viewModel.entryText
        viewModel.entryDelete(TextRange(0, text.selection.start))
    },
    "cleartoend" to { viewModel ->
        val text = viewModel.entryText
        viewModel.entryDelete(TextRange(text.selection.end, text.text.length))
    },
    "linedown" to { viewModel ->
        viewModel.scroll(ScrollEvent.LINE_DOWN)
    },
    "lineup" to { viewModel ->
        viewModel.scroll(ScrollEvent.LINE_UP)
    },
    "movecursortostart" to { viewModel ->
        viewModel.entrySetSelection(TextRange(0))
    },
    "movecursortoend" to { viewModel ->
        viewModel.entrySetSelection(TextRange(viewModel.entryText.text.length))
    },
    "deletelastword" to { viewModel ->
        val text = viewModel.entryText
        val index = text.text.substring(0, text.selection.start).trim().lastIndexOfAny(listOf(" ", "\t")) + 1
        if (index < text.text.length) {
            viewModel.entryDelete(TextRange(index, text.selection.start))
        }
    },
    "pagedown" to { viewModel ->
        viewModel.scroll(ScrollEvent.PAGE_DOWN)
    },
    "pageup" to { viewModel ->
        viewModel.scroll(ScrollEvent.PAGE_UP)
    },
    "prevhistory" to { viewModel ->
        viewModel.historyPrev()
    },
    "nexthistory" to { viewModel ->
        viewModel.historyNext()
    },
    "stopscript" to { viewModel ->
        viewModel.stopScripts()
    },
    "pausescript" to { viewModel ->
        viewModel.pauseScripts()
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
)
