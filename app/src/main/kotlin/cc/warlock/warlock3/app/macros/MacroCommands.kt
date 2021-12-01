package cc.warlock.warlock3.app.macros

import cc.warlock.warlock3.app.viewmodel.GameViewModel
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

val macroCommands = mapOf<String, suspend (GameViewModel) -> Unit>(
    "copy" to {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(it.entryText.value.text)
        clipboard.setContents(selection, selection)
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
)