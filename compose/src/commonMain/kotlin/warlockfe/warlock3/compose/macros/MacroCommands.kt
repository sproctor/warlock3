package warlockfe.warlock3.compose.macros

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.input.getSelectedText
import warlockfe.warlock3.compose.ui.game.GameViewModel
import warlockfe.warlock3.compose.ui.window.ScrollEvent
import warlockfe.warlock3.compose.util.createClipEntry
import warlockfe.warlock3.compose.util.getText

@OptIn(ExperimentalComposeUiApi::class)
val macroCommands = mapOf<String, suspend (GameViewModel, Clipboard) -> Unit>(
    "copy" to { viewModel, clipboard ->
        val textField = viewModel.entryText
        // TODO: allow focus on other windows and apply copy there
        clipboard.setClipEntry(createClipEntry(textField.getSelectedText()))
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
    }
)