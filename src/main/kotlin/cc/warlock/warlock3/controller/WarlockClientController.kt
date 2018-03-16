package cc.warlock.warlock3.controller

import cc.warlock.warlock3.model.DocumentViewModel
import cc.warlock.warlock3.model.SgeGameModel
import cc.warlock.warlock3.stormfront.SgeConnection
import cc.warlock.warlock3.stormfront.SgeGame
import cc.warlock.warlock3.view.TextEditorFragment
import tornadofx.*
import java.io.File
import java.nio.charset.Charset

class WarlockClientController : Controller() {

    /**
     * random quotes from resource quotes.txt
     */
    val quotes = File(javaClass.getResource("quotes.txt").toURI()).readLines(Charset.forName("UTF-8"))

    /**
     * the list of open text editors
     */
    val editorModelList = mutableListOf<TextEditorFragment>().observable()

    fun newEditor(): TextEditorFragment {
        val newFile = DocumentViewModel()
        newFile.title.value = "New file ${editorModelList.size}"
        newFile.commit()

        val editor = TextEditorFragment(newFile)
        editorModelList.add(editor)

        return editor
    }

    /**
     * provides a random quote
     */
    fun quote(): String = quotes[(Math.random() * quotes.size).toInt()]

    val gameModel = SgeGameModel()
    var games : Collection<SgeGame>? = null

    val connection = SgeConnection()

}