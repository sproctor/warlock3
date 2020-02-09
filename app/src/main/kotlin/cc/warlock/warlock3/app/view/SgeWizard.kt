package cc.warlock.warlock3.app.view

import cc.warlock.warlock3.app.model.AccountModel
import cc.warlock.warlock3.stormfront.network.*
import javafx.collections.FXCollections
import javafx.scene.control.Alert
import javafx.scene.control.TableView
import tornadofx.*
import java.net.ConnectException

class SgeWizard : WarlockWizard("Connect character", "Provide account details to connect using SGE"){

    init {
        val client = SgeClient()
        pages.add(AccountInput(client))
        pages.add(GameSelector(client))
        pages.add(CharacterSelector(client))
    }
}

class AccountInput(val client: SgeClient) : Page("Account") {
    private var account = AccountModel()

    override val canGoNext = account.valid(account.name, account.password)

    inner class AccountSgeListener : SgeConnectionListener {
        override fun event(event: SgeEvent) {
            when (event) {
                is SgeLoginReadyEvent ->
                    try {
                        client.login(account.name.get(), account.password.get())
                    } catch (e: ConnectException) {
                        alert(
                                type = Alert.AlertType.ERROR,
                                header = "Connection error",
                                content = e.message
                        )
                    }
                is SgeGamesReadyEvent -> isComplete = true
            }
        }
    }

    init {
        isComplete = false
        client.addListener(AccountSgeListener())
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

    override fun onSave() {
        // TODO lookup account/save account here
        try {
            confirm("Save Account", "Save account information?") {
                
            }
            client.connect()
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

class GameSelector(val client: SgeClient) : Page("Game Select") {
    inner class GameSgeListener : SgeConnectionListener {
        override fun event(event: SgeEvent) {
            when (event) {
                is SgeGamesReadyEvent -> root.items = FXCollections.observableList(event.games)
            }
        }
    }

    override val root = tableview<SgeGame> {
        columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        readonlyColumn("title", SgeGame::title)
        onSelectionChange {
            println("element selected")
            isComplete = true
        }
    }

    init {
        isComplete = false
        client.addListener(GameSgeListener())
    }

    override fun onSave() {
        try {
            client.selectGame(root.selectedItem!!)
        } catch (e: ConnectException) {
            alert(
                    type = Alert.AlertType.ERROR,
                    header = "Connection error",
                    content = e.message
            )
        }
    }
}

class CharacterSelector(val client: SgeClient) : Page("Character Select") {
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
                        client.connect()
                    }
                }
            }
        }
    }

    override val root = tableview<SgeCharacter> {
        columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        readonlyColumn("Name", SgeCharacter::name)
        onSelectionChange { isComplete = true }
    }

    init {
        isComplete = false
        client.addListener(CharacterSgeListener())
    }

    override fun onSave() {
        try {
            client.selectCharacter(root.selectedItem!!)
        } catch (e: ConnectException) {
            alert(
                    type = Alert.AlertType.ERROR,
                    header = "Connection error",
                    content = e.message
            )
        }
    }
}