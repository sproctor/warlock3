package cc.warlock.warlock3.app.macros

import cc.warlock.warlock3.app.ui.game.GameViewModel
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

val macroCommands = mapOf<String, suspend (GameViewModel) -> Unit>(
    "copy" to {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(it.entryText.value.text)
        clipboard.setContents(selection, selection)
    },
    "paste" to {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.getData(DataFlavor.stringFlavor)?.let { text ->
            it.entryAppend(text as String)
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