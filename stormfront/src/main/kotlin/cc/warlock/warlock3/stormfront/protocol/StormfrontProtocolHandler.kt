package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.stormfront.parser.StormfrontLexer
import cc.warlock.warlock3.stormfront.parser.StormfrontParser
import cc.warlock.warlock3.stormfront.protocol.elements.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.util.*

class StormfrontProtocolHandler {
    private val elementStack = LinkedList<StartElement>()
    private val elementListeners: Map<String, ElementListener> = mapOf(
        // all keys must be lowercase
        "app" to AppHandler(),
        "b" to BHandler(),
        "casttime" to CastTimeHandler(),
        "compass" to CompassHandler(),
        "compdef" to CompDefHandler(),
        "component" to ComponentHandler(),
        "dialogdata" to DialogDataHandler(),
        "dir" to DirHandler(),
        "indicator" to IndicatorHandler(),
        "left" to LeftHandler(),
        "mode" to ModeHandler(),
        "output" to OutputHandler(),
        "popbold" to PopBoldHandler(),
        "popstream" to PopStreamHandler(),
        "progressbar" to ProgressBarHandler(),
        "prompt" to PromptHandler(),
        "pushbold" to PushBoldHandler(),
        "pushstream" to PushStreamHandler(),
        "right" to RightHandler(),
        "roundtime" to RoundTimeHandler(),
        "settingsinfo" to SettingsInfoHandler(),
        "streamwindow" to StreamWindowHandler(),
        "spell" to SpellHandler(),
        "style" to StyleHandler(),
    )

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
        val events = LinkedList<StormfrontEvent>()
        for (content in contents) {
            when (content) {
                is StartElement -> {
                    lineHasTags = true
                    elementStack.push(content)
                    elementListeners[content.name.lowercase()]?.startElement(content)?.let {
                        events.add(it)
                    }
                }
                is EndElement -> {
                    lineHasTags = true
                    val topOfStack = elementStack.pop()
                    if (!topOfStack.name.equals(content.name, true)) {
                        println("ERROR: Received end element (${content.name}) does not match element on the top of the stack ($topOfStack)!")
                    }
                    elementListeners[content.name.lowercase()]?.endElement(content)?.let {
                        events.add(it)
                    }
                }
                is CharData -> {
                    val charEvents = elementStack.mapNotNull {
                        elementListeners[it.name.lowercase()]?.characters(content.data)
                    }

                    // call the character handlers on the CharData
                    // if none returned true (handled) then call the global handlers
                    if (charEvents.isNotEmpty()) {
                        events.addAll(charEvents)
                    } else {
                        events.add(StormfrontDataReceivedEvent(content.data))
                    }
                }
            }
        }
        // If a line has tags, ignore it when it has no text
        events.add(StormfrontEolEvent(ignoreWhenBlank = lineHasTags))

        return events
    }
}

interface ElementListener {
    fun startElement(element: StartElement): StormfrontEvent?
    fun characters(data: String): StormfrontEvent?
    fun endElement(element: EndElement): StormfrontEvent?
}

abstract class BaseElementListener : ElementListener {
    override fun startElement(element: StartElement): StormfrontEvent? = null
    override fun characters(data: String): StormfrontEvent? = null
    override fun endElement(element: EndElement): StormfrontEvent? = null
}
