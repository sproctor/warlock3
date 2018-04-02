package cc.warlock.warlock3.view

import cc.warlock.warlock3.core.*
import javafx.concurrent.Worker
import javafx.scene.control.ScrollPane
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import tornadofx.*
import java.util.*
import kotlin.concurrent.thread

// GameView is a bit of a misnomer. It consists of the text view and the text entry
class GameView(client: WarlockClient) : Fragment() {
    private val listeners = LinkedList<WarlockClient.ClientViewListener>()
    private val output = webview { }
    private val input = textfield {
        setOnAction {
            listeners.forEach { it.commandEntered(text) }
            text = ""
        }
    }
    override val root = borderpane {
        title = "Game View"
        center = output
        output.prefWidthProperty().bind(this@borderpane.widthProperty())
        output.engine.loadContent("""
            <!doctype html>
            <html>
            <head>
            <script>
                window.load = function() {
                    var observer = new MutationObserver(function(mutations, o) {
                        if (mutations[0].addedNodes.length > 0)
                            scrollToBottom();
                    });
                    observer.observe(document.body, {childList: true, subtree: true });
                });
                function scrollToBottom() {
                    window.scrollTo(0, document.body.scrollHeight);
                }
            </script>
            </head><body></body></html>
            """)
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
        runLater {
            //val container = root.center as ScrollPane
            //val atBottom = container.vvalue > 0.999

            val engine = output.engine

            if (engine.loadWorker.state == Worker.State.SUCCEEDED) {
                val doc = engine.document
                val body = doc.getElementsByTagName("body").item(0)
                val elementNode = doc.createElement("div")
                val textNode = doc.createTextNode(text.text)
                elementNode.appendChild(textNode)
                body.appendChild(elementNode)

            }

            /*if (atBottom) {
                output.layout()
                container.layout()
                container.vvalue = 1.0

            }*/
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