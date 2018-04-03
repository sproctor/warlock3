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
        output.engine.loadContent("""<!doctype html>
            <html>
            <head>
            <script>
                var atBottom = true;
                window.onload = function() {
                    var observer = new MutationObserver(function(mutations, o) {
                        if (mutations[0].addedNodes.length > 0 && atBottom)
                            scrollToBottom();
                    });
                    observer.observe(document.body, {childList: true, subtree: true });
                    window.onscroll = function(e) {
                        atBottom = window.innerHeight + window.scrollY >= document.body.offsetHeight
                    }
                }
                function scrollToBottom() {
                    window.scrollTo(0, document.body.scrollHeight);
                }
            </script>
            </head>
            <body></body>
            </html>
            """)
        bottom = input
        input.prefWidthProperty().bind(this@borderpane.widthProperty())
    }

    private inner class GameClientListener : ClientListener {
        override fun event(event: WarlockClient.ClientEvent) {
            runLater {
                when (event) {
                    is WarlockClient.ClientDataReceivedEvent -> {
                        displayString(event.text)
                    }
                    is WarlockClient.ClientDataSentEvent -> {
                        displayString(StyledString(event.text))
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

    private fun displayString(str: StyledString) {
        runLater {
            val engine = output.engine

            if (engine.loadWorker.state == Worker.State.SUCCEEDED) {
                val doc = engine.document
                val body = doc.getElementsByTagName("body").item(0)
                val elementNode = doc.createElement("div")
                for (substr in str.substrings) {
                    val spanNode = doc.createElement("span")
                    if (substr.style?.monospace == true) {
                        spanNode.setAttribute("style", "font-family: monospace")
                    }
                    val textNode = doc.createTextNode(substr.text)
                    spanNode.appendChild(textNode)
                    elementNode.appendChild(spanNode)
                }
                elementNode.setAttribute("class", "line")
                elementNode.setAttribute("style", "min-height: 1em; white-space: pre-wrap")
                body.appendChild(elementNode)
            }
        }
    }
}