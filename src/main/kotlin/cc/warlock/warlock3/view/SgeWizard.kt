package cc.warlock.warlock3.view

import cc.warlock.warlock3.model.AccountModel
import tornadofx.*

class SgeWizard : Wizard("Connect character", "Provide account details to connect using SGE"){

    init {
        add(AccountInput::class)
        add(GameSelector::class)
    }
}

class AccountInput : View("Account") {
    val account : AccountModel by inject()

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

    override fun onSave() {
        // TODO lookup account/save account here


    }
}

class GameSelector : View("Game") {
    val games : List<Game> = mutableListOf()

    override val root = form {
        field("Game") {
            tableview(games)
        }
    }
}