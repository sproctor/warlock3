package cc.warlock.warlock3.view

import cc.warlock.warlock3.controller.WarlockClientController
import cc.warlock.warlock3.model.AccountModel
import cc.warlock.warlock3.stormfront.*
import tornadofx.*

class SgeWizard : Wizard("Connect character", "Provide account details to connect using SGE"){

    init {
        add(AccountInput::class)
        add(GameSelector::class)
    }
}

class AccountInput : View("Account") {
    val account : AccountModel by inject()
    val controller: WarlockClientController by inject()

    inner class AccountSgeListener : SgeConnectionListener {
        override fun event(event: SgeEvent) {
            when (event) {
                is SgeLoginReadyEvent -> controller.connection.login(account.name.get(), account.password.get())
                is SgeGamesReadyEvent -> {
                    controller.games = event.games
                    isComplete = true
                }
            }
        }
    }

    override val root = form {
        fieldset(title) {
            field("Name") {
                textfield(account.name).required()
            }
            field("Password") {
                passwordfield(account.password).required()
            }
        }
    }

    init {
        controller.connection.addListener(AccountSgeListener())
    }

    override fun onSave() {
        println("onsave")
        // TODO lookup account/save account here
        controller.connection.connect()
        isComplete = false
    }
}

class GameSelector : View("Game") {
    val controller: WarlockClientController by inject()
    override val root = form {
        fieldset(title) {
            field("Game") {
                tableview<SgeGame> {
                    readonlyColumn("title", SgeGame::title)
                    bindSelected(controller.gameModel)
                }
            }
        }
    }
}