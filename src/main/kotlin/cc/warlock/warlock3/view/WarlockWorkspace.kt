package cc.warlock.warlock3.view

import cc.warlock.warlock3.controller.WarlockClientController
import cc.warlock.warlock3.core.WarlockClient
import javafx.application.Platform
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import tornadofx.*
import java.io.PrintStream

class WarlockWorkspace : Workspace("Editor") {
    val clientController: WarlockClientController by inject()

    init {
        menubar {
            menu("File") {
                item("Connect").action {
                    log.info("Opening SGE wizard")
                    val wizard = SgeWizard()
                    wizard.openModal()
                }
                item("New").action {
                    //workspace.dock(mainView, true)
                    log.info("Opening text file")
                    workspace.dock(clientController.newEditor(), true)
                }
                separator()
                item("Exit").action {
                    log.info("Leaving workspace")
                    Platform.exit()
                }
            }
            menu("Window"){
                item("Close all").action {
                    clientController.editorModelList.clear()
                    workspace.dock(EmptyView(),true)
                }
                separator()
                openWindowMenuItemsAtfer()
            }
            menu("Help") {
                item("About...")
            }

        }

        add(RestProgressBar::class)
        with(bottomDrawer) {
            item( "Logs") {
                textarea {
                    addClass("consola")
                    val ps = PrintStream(TextAreaOutputStream(this))
                    System.setErr(ps)
                    System.setOut(ps)
                }

            }
        }
    }

    fun openGameView(client: WarlockClient) {
        workspace.dock(clientController.newGameView(client), true)
    }

    /**
     * this extension method allows binding the open document's fragment to menu
     */
    private fun Menu.openWindowMenuItemsAtfer() {
        clientController.editorModelList.onChange { dvm ->
            dvm.next()
            if (dvm.wasAdded()) {
                dvm.addedSubList.forEach { x ->
                    val item = MenuItem(x.title)
                    item.action {
                        workspace.dock(x, true)
                    }
                    items.add(item)
                }
            } else if (dvm.wasRemoved()) {
                dvm.removed.forEach { x ->
                    workspace.viewStack.remove(x)
                    x.close()
                    println(workspace.dockedComponent)
                    val morituri = items.takeLast(items.size - 2).filter { item -> item.text.equals(x.title) }
                    items.removeAll(morituri)
                }
            }
        }
    }

}