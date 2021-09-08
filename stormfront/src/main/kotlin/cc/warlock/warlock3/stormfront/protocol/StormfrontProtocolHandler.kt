package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.parser.StormfrontLexer
import cc.warlock.warlock3.stormfront.parser.StormfrontParser
import cc.warlock.warlock3.stormfront.protocol.elements.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.util.*

class StormfrontProtocolHandler {
    private val elementStack = LinkedList<StartElement>()
    private val elementListeners: Map<String, ElementListener> = mapOf(
        "app" to AppHandler(),
        "mode" to ModeHandler(),
        "output" to OutputHandler(),
        "popBold" to PopBoldHandler(),
        "prompt" to PromptHandler(),
        "pushBold" to PushBoldHandler(),
        "roundTime" to RoundTimeHandler(),
    )
    private val styleStack = Stack<WarlockStyle>()
    private var outputStyle: WarlockStyle? = null

    fun parseLine(line: String): List<StormfrontEvent> {
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

    private fun handleContent(contents: List<Content>): List<StormfrontEvent> {
        // FIXME: this is kind of hacky
        var lineHasTags = false
        var lineHasText = false
        val events = LinkedList<StormfrontEvent>()
        for (content in contents) {
            when (content) {
                is StartElement -> {
                    lineHasTags = true
                    elementStack.push(content)
                    elementListeners[content.name]?.startElement(content)?.let {
                        events.add(it)
                    }
                }
                is EndElement -> {
                    lineHasTags = true
                    if (elementStack.pop().name != content.name) {
                        println("ERROR: Received end element does not match element on the top of the stack!")
                    }
                    elementListeners[content.name]?.endElement(content)?.let {
                        events.add(it)
                    }
                }
                is CharData -> {
                    lineHasText = true
                    val listener = elementStack.peek()?.name?.let { elementListeners[it] }

                    // call the character handlers on the CharData
                    // if none returned true (handled) then call the global handlers
                    if (listener != null) {
                        listener.characters(content.data)?.let { events.add(it) }
                    } else {
                        events.add(StormfrontDataReceivedEvent(content.data))
                    }
                }
            }
        }
        // If a line has just tags, don't send the newline, otherwise do.
        if ((lineHasText || !lineHasTags) && elementStack.isEmpty() ) {
            events.add(StormfrontEolEvent)
        }

        return events
    }
}

interface ElementListener {
    fun startElement(element: StartElement): StormfrontEvent?
    fun characters(data: String): StormfrontEvent?
    fun endElement(element: EndElement): StormfrontEvent?
}

abstract class BaseElementListener : ElementListener{
    override fun startElement(element: StartElement): StormfrontEvent? = null
    override fun characters(data: String): StormfrontEvent? = null
    override fun endElement(element: EndElement): StormfrontEvent? = null
}
