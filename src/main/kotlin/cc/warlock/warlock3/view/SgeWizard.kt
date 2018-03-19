package cc.warlock.warlock3.view

import cc.warlock.warlock3.controller.WarlockClientController
import cc.warlock.warlock3.model.AccountModel
import cc.warlock.warlock3.stormfront.network.*
import javafx.collections.FXCollections
import javafx.scene.control.Alert
import tornadofx.*
import java.net.ConnectException

class SgeWizard : Wizard("Connect character", "Provide account details to connect using SGE"){
    override val canFinish = allPagesComplete
    override val canGoNext = currentPageComplete

    init {
        add(AccountInput::class)
        add(GameSelector::class)
        add(CharacterSelector::class)
    }
}

class AccountInput : View("Account") {
    private val account : AccountModel by inject()
    private val controller: WarlockClientController by inject()

    override val complete = account.valid(account.name, account.password)

    inner class AccountSgeListener : SgeConnectionListener {
        override fun event(event: SgeEvent) {
            when (event) {
                is SgeLoginReadyEvent ->
                    try {
                        controller.connection.login(account.name.get(), account.password.get())
                    } catch (e: ConnectException) {
                        alert(
                                type = Alert.AlertType.ERROR,
                                header = "Connection error",
                                content = e.message
                        )
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
        // TODO lookup account/save account here
        try {
            controller.connection.connect()
        } catch (e: ConnectException) {
            // TODO fail the click on "next" here
            alert(
                    type = Alert.AlertType.ERROR,
                    header = "Connection error",
                    content = e.message
            )
        }
    }
}

class GameSelector : View("Game Select") {
    private val controller: WarlockClientController by inject()

    inner class GameSgeListener : SgeConnectionListener {
        override fun event(event: SgeEvent) {
            when (event) {
                is SgeGamesReadyEvent -> root.items = FXCollections.observableList(event.games)
            }
        }
    }

    override val root = tableview<SgeGame> {
        readonlyColumn("title", SgeGame::title).pctWidth(100.0)
        onSelectionChange {
            println("element selected")
            isComplete = true
        }
    }

    init {
        isComplete = false
        controller.connection.addListener(GameSgeListener())
    }

    override fun onSave() {
        try {
            controller.connection.selectGame(root.selectedItem!!)
        } catch (e: ConnectException) {
            alert(
                    type = Alert.AlertType.ERROR,
                    header = "Connection error",
                    content = e.message
            )
        }
    }
}

class CharacterSelector : View("Character Select") {
    private val controller: WarlockClientController by inject()

    inner class CharacterSgeListener : SgeConnectionListener {
        override fun event(event: SgeEvent) {
            runLater {
                when (event) {
                    is SgeCharactersReadyEvent -> root.items = FXCollections.observableList(event.characters)
                    is SgeReadyToPlayEvent -> {
                        val workspace = find(WarlockWorkspace::class)
                        val properties = event.loginProperties
                        val client = StormfrontClient(properties["GAMEHOST"]!!, properties["GAMEPORT"]!!.toInt(),
                                properties["KEY"]!!)
                        workspace.openGameView(client)
                    }
                }
            }
        }
    }

    override val root = tableview<SgeCharacter> {
        readonlyColumn("Name", SgeCharacter::name).pctWidth(100.0)
        onSelectionChange { isComplete = true }
    }

    init {
        isComplete = false
        controller.connection.addListener(CharacterSgeListener())
    }

    override fun onSave() {
        try {
            controller.connection.selectCharacter(root.selectedItem!!)
        } catch (e: ConnectException) {
            alert(
                    type = Alert.AlertType.ERROR,
                    header = "Connection error",
                    content = e.message
            )
        }
    }
}