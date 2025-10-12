package warlockfe.warlock3.compose.macros

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.getSelectedText
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.ScrollEvent
import warlockfe.warlock3.compose.util.createClipEntry
import warlockfe.warlock3.compose.util.getText

@OptIn(ExperimentalComposeUiApi::class)
val macroCommands = mapOf<String, suspend (GameViewModel, Clipboard) -> Unit>(
    "bufferend" to { viewModel, _ ->
        viewModel.scroll(ScrollEvent.BUFFER_END)
    },
    "bufferstart" to { viewModel, _ ->
        viewModel.scroll(ScrollEvent.BUFFER_START)
    },
    "cleartostart" to { viewModel, _ ->
        val text = viewModel.entryText
        viewModel.entryDelete(TextRange(0, text.selection.start))
    },
    "cleartoend" to { viewModel, _ ->
        val text = viewModel.entryText
        viewModel.entryDelete(TextRange(text.selection.end, text.text.length))
    },
    "copy" to { viewModel, clipboard ->
        val textField = viewModel.entryText
        // TODO: allow focus on other windows and apply copy there
        clipboard.setClipEntry(createClipEntry(textField.text.substring(textField.selection.start, textField.selection.end)))
    },
    "linedown" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.LINE_DOWN)
    },
    "lineup" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.LINE_UP)
    },
    "movecursortostart" to { viewModel, _ ->
        viewModel.entrySetSelection(TextRange(0))
    },
    "movecursortoend" to { viewModel, _ ->
        viewModel.entrySetSelection(TextRange(viewModel.entryText.text.length))
    },
    "deletelastword" to { viewModel, _ ->
        val text = viewModel.entryText
        val index = text.text.substring(0, text.selection.start).trim().lastIndexOfAny(listOf(" ", "\t")) + 1
        if (index < text.text.length) {
            viewModel.entryDelete(TextRange(index, text.selection.start))
        }
    },
    "pagedown" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.PAGE_DOWN)
    },
    "pageup" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.PAGE_UP)
    },
    "paste" to { viewModel, clipboard ->
        clipboard.getClipEntry()?.getText()?.let { clipText ->
            viewModel.entryInsert(clipText)
        }
    },
    "prevhistory" to { viewModel, _ ->
        viewModel.historyPrev()
    },
    "nexthistory" to { viewModel, _ ->
        viewModel.historyNext()
    },
    "stopscript" to { viewModel, _ ->
        viewModel.stopScripts()
    },
    "pausescript" to { viewModel, _ ->
        viewModel.pauseScripts()
    },
    "repeatlast" to { viewModel, _ ->
        viewModel.repeatCommand(1)
    },
    "returnorrepeatlast" to { viewModel, _ ->
        if (viewModel.entryText.text.isBlank()) {
            viewModel.repeatCommand(1)
        } else {
            viewModel.submit()
        }
    },
    "repeatsecondtolast" to { viewModel, _ ->
        viewModel.repeatCommand(2)
    },
    "selectall" to { viewModel, _ ->
        val text = viewModel.entryText
        viewModel.entrySetSelection(TextRange(0, text.text.length))
    },
)
