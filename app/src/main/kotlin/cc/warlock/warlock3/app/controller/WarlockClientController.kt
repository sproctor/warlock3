package cc.warlock.warlock3.app.controller

import cc.warlock.warlock3.core.WarlockClient
import cc.warlock.warlock3.app.model.DocumentViewModel
import cc.warlock.warlock3.app.view.GameView
import cc.warlock.warlock3.app.view.TextEditorFragment
import tornadofx.Controller
import tornadofx.observable

class WarlockClientController : Controller() {

    /**
     * the list of open text editors
     */
    val editorModelList = mutableListOf<TextEditorFragment>().observable()

    val gameViewList = mutableListOf<GameView>().observable()

    fun newEditor(): TextEditorFragment {
        val newFile = DocumentViewModel()
        newFile.title.value = "New file ${editorModelList.size}"
        newFile.commit()

        val editor = TextEditorFragment(newFile)
        editorModelList.add(editor)

        return editor
    }

    fun newGameView(client: WarlockClient): GameView {
        val gameView = GameView(client)
        gameView.addListener(client.getClientViewListener())
        gameViewList.add(gameView)
        return gameView
    }

    /*val configs = Configurations()
    val acountConfigBuilder = configs.propertiesBuilder(System.getProperty("user.home")
            + "/.warlock3/account.properties")
    val accountsConfig = acountConfigBuilder.configuration*/
}