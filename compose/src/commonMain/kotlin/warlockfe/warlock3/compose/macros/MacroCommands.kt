package warlockfe.warlock3.compose.macros

import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.ScrollEvent

val macroCommands = mapOf<String, suspend (GameViewModel, ClipboardManager) -> Unit>(
    "copy" to { viewModel, clipboard ->
        val textField = viewModel.entryText
        val text = textField.text.substring(textField.selection.start, textField.selection.end)
        clipboard.setText(AnnotatedString(text))
    },
    "linedown" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.LINE_DOWN)
    },
    "lineup" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.LINE_UP)
    },
    "pagedown" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.PAGE_DOWN)
    },
    "pageup" to { viewModel, clipboard ->
        viewModel.scroll(ScrollEvent.PAGE_UP)
    },
    "paste" to { viewModel, clipboard ->
        clipboard.getText()?.let { text ->
            viewModel.entryInsert(text.text)
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
        viewModel.repeatCommand(0)
    },
    "returnorrepeatlast" to { viewModel, _ ->
        if (viewModel.entryText.text.isBlank()) {
            viewModel.repeatCommand(0)
        } else {
            viewModel.submit()
        }
    },
    "repeatsecondtolast" to { viewModel, _ ->
        viewModel.repeatCommand(1)
    }
)