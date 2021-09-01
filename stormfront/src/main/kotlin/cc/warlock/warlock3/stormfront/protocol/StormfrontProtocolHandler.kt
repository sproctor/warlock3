package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.core.ClientDataReceivedEvent
import cc.warlock.warlock3.core.ClientEolEvent
import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.stormfront.parser.StormfrontLexer
import cc.warlock.warlock3.stormfront.parser.StormfrontParser
import cc.warlock.warlock3.stormfront.protocol.elements.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.util.*

class StormfrontProtocolHandler {
    private val elementStack = LinkedList<StartElement>()
    private val elementListeners: Map<String, ElementListener> = mapOf(
        "mode" to ModeHandler(),
        "output" to OutputHandler(),
        "popBold" to PopBoldHandler(),
        "prompt" to PromptHandler(),
        "pushBold" to PushBoldHandler(),
        "roundTime" to RoundTimeHandler(),
    )

    fun parseLine(line: String): List<ClientEvent> {
        return try {
            val inputStream = CharStreams.fromString(line)
            val lexer = StormfrontLexer(inputStream)
            val tokens = CommonTokenStream(lexer)
            val parser = StormfrontParser(tokens)
            val contents = StormfrontNodeVisitor.visitDocument(parser.document())
            handleContent(contents)
        } catch (e: Exception) {
            println("Encountered a parse error")
            emptyList()
        }
    }

    private fun handleContent(contents: List<Content>): List<ClientEvent> {
        // FIXME: this is kind of hacky
        var lineHasTags = false
        var lineHasText = false
        val events = LinkedList<ClientEvent>()
        for (content in contents) {
            when (content) {
                is StartElement -> {
                    lineHasTags = true
                    elementStack.push(content)
                    elementListeners[content.name]?.startElement(content)?.let {
                        events.addAll(it)
                    }
                }
                is EndElement -> {
                    lineHasTags = true
                    if (elementStack.pop().name != content.name) {
                        println("ERROR: Received end element does not match element on the top of the stack!")
                    }
                    elementListeners[content.name]?.endElement(content)?.let {
                        events.addAll(it)
                    }
                }
                is CharData -> {
                    lineHasText = true
                    val listener = elementStack.peek()?.name?.let { elementListeners[it] }

                    // call the character handlers on the CharData
                    // if none returned true (handled) then call the global handlers
                    if (listener != null) {
                        events.addAll(listener.characters(content.data))
                    } else {
                        events.add(ClientDataReceivedEvent(content.data))
                    }
                }
            }
        }
        // If a line has just tags, don't send the newline, otherwise do.
        if ((lineHasText || !lineHasTags) && elementStack.isEmpty() ) {
            events.add(ClientEolEvent)
        }

        return events
    }
}

interface ElementListener {
    fun startElement(element: StartElement): List<ClientEvent>
    fun characters(data: String): List<ClientEvent>
    fun endElement(element: EndElement): List<ClientEvent>
}

abstract class BaseElementListener : ElementListener{
    override fun startElement(element: StartElement): List<ClientEvent> = emptyList()
    override fun characters(data: String): List<ClientEvent> = emptyList()
    override fun endElement(element: EndElement): List<ClientEvent> = emptyList()
}
