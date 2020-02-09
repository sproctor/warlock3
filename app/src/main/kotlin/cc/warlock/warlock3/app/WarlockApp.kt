package cc.warlock.warlock3.app

import cc.warlock.warlock3.app.view.WarlockWorkspace
import javafx.application.Platform
import tornadofx.*
import kotlin.system.exitProcess

class WarlockApp : App() {
    override val primaryView = WarlockWorkspace::class

    init {
        importStylesheet(Styles::class)
    }

    override fun stop() {
        super.stop()
        Platform.exit()
        exitProcess(0)
    }
}