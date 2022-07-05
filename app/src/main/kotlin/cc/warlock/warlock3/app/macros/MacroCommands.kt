package cc.warlock.warlock3.app.macros

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.app.ui.game.GameViewModel

val macroCommands = mapOf<String, suspend (GameViewModel) -> Unit>(
    "copy" to {
        val textField = it.entryText.value
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
        it.repeatLastCommand()
    },
)