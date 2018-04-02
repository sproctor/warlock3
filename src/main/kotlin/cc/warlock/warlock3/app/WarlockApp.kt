package cc.warlock.warlock3.app

import cc.warlock.warlock3.view.WarlockWorkspace
import javafx.application.Platform
import tornadofx.*

class WarlockApp : App() {
    override val primaryView = WarlockWorkspace::class

    init {
        importStylesheet(Styles::class)
    }

    override fun stop() {
        super.stop()
        Platform.exit()
        System.exit(0)
    }
}