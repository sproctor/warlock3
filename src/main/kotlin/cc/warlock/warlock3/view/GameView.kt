package cc.warlock.warlock3.view

import cc.warlock.warlock3.core.*
import javafx.scene.control.ScrollPane
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import tornadofx.*
import java.util.*

// GameView is a bit of a misnomer. It consists of the text view and the text entry
class GameView(client: WarlockClient) : Fragment() {
    private val listeners = LinkedList<WarlockClient.ClientViewListener>()
    private val output = textflow { }
    private val input = textfield {
        setOnAction {
            listeners.forEach { it.commandEntered(text) }
            text = ""
        }
    }
    override val root = borderpane {
        title = "Game View"
        center = scrollpane {
            add(output)
            setFitToWidth(true)
            prefWidthProperty().bind(this@borderpane.widthProperty())
        }
        bottom = input
        input.prefWidthProperty().bind(this@borderpane.widthProperty())
    }

    private inner class GameClientListener : ClientListener {
        override fun event(event: WarlockClient.ClientEvent) {
            runLater {
                when (event) {
                    is WarlockClient.ClientDataReceivedEvent -> {
                        event.text.toText().forEach { displayText(it) }
                    }
                    is WarlockClient.ClientDataSentEvent -> {
                        displayText(Text(event.text))
                    }
                }
            }
        }
    }

    init {
        client.addListener(GameClientListener())
    }

    fun addListener(listener: WarlockClient.ClientViewListener) {
        listeners.add(listener)
    }

    private fun displayText(text: Text) {
        val container = root.center as ScrollPane
        val atBottom = container.vvalue > 0.999

        output.children.add(text)

        if (atBottom) {
            output.layout()
            container.layout()
            container.vvalue = 1.0

        }
    }
}

fun StyledString.toText(): List<Text> {
    return substrings.map {
        val text = Text(it.text)
        if (it.style != null)
            text.applyStyle(it.style!!)
        text
    }
}

fun Text.applyStyle(style: WarlockStyle) {
    //fill = style.
    if (style.monospace)
        font = Font.font("Monospaced")
}

fun WarlockColor.toColor(): Color {
    return Color.rgb(red, green, blue)
}