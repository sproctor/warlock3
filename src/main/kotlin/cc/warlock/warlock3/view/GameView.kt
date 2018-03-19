package cc.warlock.warlock3.view

import cc.warlock.warlock3.core.ClientListener
import cc.warlock.warlock3.core.WarlockClient
import javafx.scene.text.Text
import tornadofx.*

class GameView(val client: WarlockClient) : Fragment() {
    val output = textarea {
        isEditable = false
        isWrapText = true
    }
    val input = textfield {
        setOnAction {
            client.send(text)
            text = ""
        }
    }
    override val root = borderpane {
        title = "Game View"
        center = output
        output.prefWidthProperty().bind(this@borderpane.widthProperty());
        bottom = input
        input.prefWidthProperty().bind(this@borderpane.widthProperty());
    }

    private inner class GameClientListener() : ClientListener {
        override fun event(event: WarlockClient.ClientEvent) {
            runLater {
                when (event) {
                    is WarlockClient.ClientDataReceivedEvent -> {
                        output.appendText(event.data)
                    }
                    is WarlockClient.ClientDataSentEvent -> {
                        output.appendText(event.data)
                    }
                }
            }
        }
    }

    init {
        client.addListener(GameClientListener())
    }
}