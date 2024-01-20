package warlockfe.warlock3.compose.macros

import androidx.compose.ui.text.AnnotatedString
import warlockfe.warlock3.compose.ui.game.GameViewModel

val macroCommands = mapOf<String, suspend (GameViewModel) -> Unit>(
    "copy" to {
        val textField = it.entryText
        val text = textField.text.substring(textField.selection.start, textField.selection.end)
        it.clipboard.setText(AnnotatedString(text))
    },
    "paste" to {
        it.clipboard.getText()?.let { text ->
            it.entryInsert(text.text)
        }
    },
    "prevhistory" to {
        it.historyPrev()
    },
    "nexthistory" to {
        it.historyNext()
    },
    "stopscript" to {
        it.stopScripts()
    },
    "pausescript" to {
        it.pauseScripts()
    },
    "repeatlast" to {
        it.repeatCommand(0)
    },
    "returnorrepeatlast" to {
        if (it.entryText.text.isBlank()) {
            it.repeatCommand(0)
        } else {
            it.submit()
        }
    },
    "repeatsecondtolast" to {
        it.repeatCommand(1)
    }
)